// Copyright 2016 The Vanadium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import '../base/common.dart';
import '../mobile/device.dart';
import '../mobile/device_spec.dart';
import '../mobile/android.dart';
import '../test/coverage_collector.dart';
import '../globals.dart';
import '../util.dart';

class MDTestRunner {
  List<Process> appProcesses;

  MDTestRunner() {
    appProcesses = <Process>[];
  }

  /// Invoke runApp function for each device spec to device mapping in parallel
  Future<int> runAllApps(Map<DeviceSpec, Device> deviceMapping) async {
    List<Future<int>> runAppList = <Future<int>>[];
    for (DeviceSpec deviceSpec in deviceMapping.keys) {
      Device device = deviceMapping[deviceSpec];
      runAppList.add(runApp(deviceSpec, device));
    }
    int res = 0;
    List<int> results = await Future.wait(runAppList);
    for (int result in results)
        res += result;
    return res == 0 ? 0 : 1;
  }

  /// Create a process that runs 'flutter run ...' command which installs and
  /// starts the app on the device.  The function finds a observatory port
  /// through the process output.  If no observatory port is found, then report
  /// error.
  Future<int> runApp(DeviceSpec deviceSpec, Device device) async {
    if (await unlockDevice(device) != 0) {
      printError('Device ${device.id} fails to wake up.');
      return 1;
    }

    Process process = await Process.start(
      'flutter',
      ['run', '-d', '${device.id}', '--target=${deviceSpec.appPath}'],
      workingDirectory: deviceSpec.appRootPath
    );
    appProcesses.add(process);
    Stream lineStream = process.stdout
                               .transform(new Utf8Decoder())
                               .transform(new LineSplitter());
    RegExp portPattern = new RegExp(r'Observatory listening on (http.*)');
    await for (var line in lineStream) {
      print(line.toString().trim());
      Match portMatch = portPattern.firstMatch(line.toString());
      if (portMatch != null) {
        deviceSpec.observatoryUrl = portMatch.group(1);
        break;
      }
    }

    process.stderr.drain();

    if (deviceSpec.observatoryUrl == null) {
      printError('No observatory url is found.');
      return 1;
    }

    return 0;
  }

  /// Run all tests
  Future<int> runAllTests(Iterable<String> testPaths) async {
    int result = 0;
    for (String testPath in testPaths) {
      result += await runTest(testPath);
    }
    return result == 0 ? 0 : 1;
  }

  /// Create a process and invoke 'dart testPath' to run the test script.  After
  /// test result is returned (either pass or fail), kill all app processes and
  /// return the current process exit code
  Future<int> runTest(String testPath) async {
    Process process = await Process.start('dart', ['$testPath']);
    RegExp testStopPattern = new RegExp(r'All tests passed|Some tests failed');
    Stream stdoutStream = process.stdout
                                 .transform(new Utf8Decoder())
                                 .transform(new LineSplitter());
    await for (var line in stdoutStream) {
      print(line.toString().trim());
      if (testStopPattern.hasMatch(line.toString()))
        break;
    }
    process.stderr.drain();
    return await process.exitCode;
  }

  /// Kill all app processes
  Future<Null> killAppProcesses() async {
    for (Process process in appProcesses) {
      process.kill();
    }
  }
}

/// Create a coverage collector for each application and assign a coverage
/// collection task for the coverage collector
void buildCoverageCollectionTasks(
  Map<DeviceSpec, Device> deviceMapping,
  Map<String, CoverageCollector> collectorPool
) {
  assert(collectorPool != null);
  // Build app path to coverage collector mapping and add collection tasks
  deviceMapping.keys.forEach((DeviceSpec spec) {
    collectorPool.putIfAbsent(
      spec.appRootPath,
      () => new CoverageCollector()
    ).collectCoverage(spec.observatoryUrl);
  });
}

/// Run coverage collection tasks for each application
Future<Null> runCoverageCollectionTasks(
  Map<String, CoverageCollector> collectorPool
) async {
  assert(collectorPool.isNotEmpty);
  // Collect coverage for every application
  for (CoverageCollector collector in collectorPool.values) {
    await collector.finishPendingJobs();
  }
}

/// Compute application code coverage and write coverage info in lcov format
Future<int> computeAppsCoverage(
  Map<String, CoverageCollector> collectorPool,
  String commandName
) async {
  if (collectorPool.isEmpty)
    return 1;
  // Write coverage info to coverage/code_coverage folder under each
  // application folder
  for (String appRootPath in collectorPool.keys) {
    CoverageCollector collector = collectorPool[appRootPath];
    String coverageData = await collector.finalizeCoverage(appRootPath);
    if (coverageData == null)
      return 1;

    String coveragePath = normalizePath(
      appRootPath,
      '$defaultCodeCoverageDirectoryPath',
      'cov_${commandName}_${generateTimeStamp()}.info'
    );
    try {
      // Write coverage info to code_coverage folder
      new File(coveragePath)
        ..createSync(recursive: true)
        ..writeAsStringSync(coverageData, flush: true);
      print('Writing code coverage to $coveragePath');
    } on FileSystemException {
      printError('Cannot write code coverage info to $coveragePath');
      return 1;
    }
  }
  return 0;
}
