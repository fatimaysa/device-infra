/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.devtools.deviceaction.framework.devices;

import static com.google.devtools.deviceaction.common.utils.TimeUtils.fromProtoDuration;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.deviceaction.common.annotations.Annotations.Configurable;
import com.google.devtools.deviceaction.common.annotations.Annotations.SpecValue;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.common.utils.BundletoolUtil;
import com.google.devtools.deviceaction.common.utils.LazyCached;
import com.google.devtools.deviceaction.framework.proto.AndroidPhoneSpec;
import com.google.devtools.deviceaction.framework.proto.DeviceType;
import com.google.devtools.deviceinfra.shared.util.time.Sleeper;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.platform.android.packagemanager.ModuleInfo;
import com.google.devtools.mobileharness.platform.android.packagemanager.PackageInfo;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.shared.autovalue.UtilArgs;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.platform.android.systemstate.AndroidSystemStateUtil;
import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.SortedSet;
import java.util.concurrent.ExecutionException;

/** A {@link Device} class for Android phones. */
@Configurable(specType = AndroidPhoneSpec.class)
public class AndroidPhone implements Device {

  private static final String GOOGLE = "google";
  private static final String DEV_KEYS = "dev-keys";

  @VisibleForTesting static final Duration DEFAULT_DEVICE_READY_TIMEOUT = Duration.ofMinutes(5);
  private final AndroidAdbUtil androidAdbUtil;
  private final AndroidFileUtil androidFileUtil;
  private final AndroidPackageManagerUtil androidPackageManagerUtil;
  private final AndroidSystemSettingUtil androidSystemSettingUtil;

  private final AndroidSystemStateUtil androidSystemStateUtil;
  private final BundletoolUtil bundletoolUtil;

  private final Sleeper sleeper;

  private final String uuid;

  private final AndroidPhoneSpec spec;

  private final LazyCached<Path> deviceSpecFileProvider =
      new LazyCached<Path>() {
        @Override
        protected Path provide() throws DeviceActionException, InterruptedException {
          return bundletoolUtil.generateDeviceSpecFile(uuid);
        }
      };

  private final LoadingCache<AndroidProperty, String> propertyCache =
      CacheBuilder.newBuilder()
          .build(
              new CacheLoader<AndroidProperty, String>() {
                @Override
                public String load(AndroidProperty property)
                    throws DeviceActionException, InterruptedException {
                  try {
                    return androidAdbUtil.getProperty(uuid, property);
                  } catch (MobileHarnessException e) {
                    throw new DeviceActionException(e, "Failed to get the property %s.", property);
                  }
                }
              });

  protected AndroidPhone(
      AndroidAdbUtil androidAdbUtil,
      AndroidFileUtil androidFileUtil,
      AndroidPackageManagerUtil androidPackageManagerUtil,
      AndroidSystemSettingUtil androidSystemSettingUtil,
      AndroidSystemStateUtil androidSystemStateUtil,
      BundletoolUtil bundletoolUtil,
      Sleeper sleeper,
      String uuid,
      AndroidPhoneSpec deviceSpec) {
    this.androidAdbUtil = androidAdbUtil;
    this.androidFileUtil = androidFileUtil;
    this.androidPackageManagerUtil = androidPackageManagerUtil;
    this.androidSystemSettingUtil = androidSystemSettingUtil;
    this.androidSystemStateUtil = androidSystemStateUtil;
    this.bundletoolUtil = bundletoolUtil;
    this.sleeper = sleeper;
    this.uuid = uuid;
    this.spec = deviceSpec;
  }

  @Override
  public String getUuid() {
    return uuid;
  }

  @Override
  public DeviceType getDeviceType() {
    return DeviceType.ANDROID_PHONE;
  }

  public SortedSet<PackageInfo> listPackages() throws DeviceActionException, InterruptedException {
    try {
      return androidPackageManagerUtil.listPackageInfos(uuid);
    } catch (MobileHarnessException e) {
      throw new DeviceActionException(e, "Failed to list the packages.");
    }
  }

  public SortedSet<PackageInfo> listApexPackages()
      throws DeviceActionException, InterruptedException {
    try {
      return androidPackageManagerUtil.listApexPackageInfos(uuid);
    } catch (MobileHarnessException e) {
      throw new DeviceActionException(e, "Failed to list the apex packages.");
    }
  }

  public SortedSet<ModuleInfo> listModules() throws DeviceActionException, InterruptedException {
    try {
      return androidPackageManagerUtil.listModuleInfos(uuid);
    } catch (MobileHarnessException e) {
      throw new DeviceActionException(e, "Failed to list the modules.");
    }
  }

  public Path getDeviceSpecFilePath() throws DeviceActionException, InterruptedException {
    return deviceSpecFileProvider.call();
  }

  public int getSdkVersion() throws DeviceActionException {
    try {
      return Integer.parseInt(propertyCache.get(AndroidProperty.SDK_VERSION));
    } catch (ExecutionException e) {
      throw new DeviceActionException(
          "EXECUTION_ERROR", ErrorType.INFRA_ISSUE, "Failed to get cache property.", e);
    }
  }

  /**
   * Removes all files and directories that match the {@code fileOrDirPathPattern} regex pattern.
   */
  public void removeFiles(String fileOrDirPathPattern)
      throws DeviceActionException, InterruptedException {
    try {
      androidFileUtil.removeFiles(uuid, fileOrDirPathPattern);
    } catch (MobileHarnessException e) {
      throw new DeviceActionException(e, "Failed to remove the file.");
    }
  }

  public SortedSet<String> listFiles(String filePath)
      throws DeviceActionException, InterruptedException {
    try {
      return androidFileUtil.listFilesInOrder(uuid, filePath);
    } catch (MobileHarnessException e) {
      throw new DeviceActionException(e, "Failed to list the files.");
    }
  }

  public ImmutableList<String> getAllInstalledPaths(String packageName)
      throws DeviceActionException, InterruptedException {
    try {
      return androidPackageManagerUtil.getAllInstalledPaths(
          UtilArgs.builder().setSerial(uuid).build(), packageName);
    } catch (MobileHarnessException e) {
      throw new DeviceActionException(e, "Failed to get path for package %s.", packageName);
    }
  }

  // TODO: b/279367659 Implement in a separate cl.
  /** Installs apk or apex packages. Returns {@code true} if reboot is needed. */
  public boolean installPackages(Multimap<String, File> packageFiles, String... extraArgs)
      throws DeviceActionException, InterruptedException {
    return false;
  }

  // TODO: b/279367659 Implement in a separate cl.
  /** Installs packages from apks. Returns {@code true} if reboot is needed. */
  public boolean installBundledPackages(List<File> apksList, String... extraArgs)
      throws DeviceActionException, InterruptedException {
    return false;
  }

  // TODO: b/279367659 Implement in a separate cl.
  public boolean installZippedTrain(File train, String... extraArgs)
      throws DeviceActionException, InterruptedException {
    return false;
  }

  // TODO: b/279367659 Implement in a separate cl.
  public void reboot() throws DeviceActionException, InterruptedException {}

  // TODO: b/279367659 Implement in a separate cl.
  public void enableTestharness() throws DeviceActionException, InterruptedException {}

  public void becomeRoot() throws DeviceActionException, InterruptedException {
    try {
      androidSystemStateUtil.becomeRoot(uuid);
    } catch (MobileHarnessException e) {
      throw new DeviceActionException(e, "Fail to root.");
    }
  }

  public void push(Path srcOnHost, Path desOnDevice)
      throws DeviceActionException, InterruptedException {
    try {
      androidFileUtil.push(uuid, getSdkVersion(), srcOnHost.toString(), desOnDevice.toString());
    } catch (MobileHarnessException e) {
      throw new DeviceActionException(
          e, "Failed to push %s to %s on %s", srcOnHost, desOnDevice, uuid);
    }
  }

  public void remount() throws DeviceActionException, InterruptedException {
    becomeRoot();
    try {
      androidFileUtil.remount(uuid, /* checkResults= */ true);
    } catch (MobileHarnessException e) {
      throw new DeviceActionException(e, "Failed to remount");
    }
  }

  // TODO: b/279367659 Implement in a separate cl.
  public void disableVerity() throws DeviceActionException, InterruptedException {}

  // TODO: b/279367659 Implement in a separate cl.
  /** Extracts installation files for the device from an apks file. */
  public ImmutableList<File> extractFilesFromApks(File packageFile)
      throws DeviceActionException, InterruptedException {
    return ImmutableList.of();
  }

  public boolean devKeySigned() throws DeviceActionException {
    try {
      return Ascii.equalsIgnoreCase(brand(), GOOGLE)
          && Ascii.equalsIgnoreCase(propertyCache.get(AndroidProperty.SIGN), DEV_KEYS);
    } catch (ExecutionException e) {
      throw new DeviceActionException(
          "EXECUTION_ERROR", ErrorType.INFRA_ISSUE, "Failed to get cache property.", e);
    }
  }

  @SpecValue(field = "brand")
  public String brand() {
    return spec.getBrand();
  }

  @SpecValue(field = "reboot_await")
  public Duration rebootAwait() {
    return fromProtoDuration(spec.getRebootAwait());
  }

  @SpecValue(field = "reboot_timeout")
  public Duration rebootTimeout() {
    return fromProtoDuration(spec.getRebootTimeout());
  }

  @SpecValue(field = "testharness_boot_await")
  public Duration testharnessBootAwait() {
    return fromProtoDuration(spec.getTestharnessBootAwait());
  }

  @SpecValue(field = "testharness_boot_timeout")
  public Duration testharnessBootTimeout() {
    return fromProtoDuration(spec.getTestharnessBootTimeout());
  }

  @SpecValue(field = "stage_ready_timeout")
  public Duration stageReadyTimeout() {
    return fromProtoDuration(spec.getStagedReadyTimeout());
  }

  @SpecValue(field = "extra_wait_for_staging")
  public Duration extraWaitForStaging() {
    return fromProtoDuration(spec.getExtraWaitForStaging());
  }

  @SpecValue(field = "need_disable_package_cache")
  public boolean needDisablePackageCache() {
    return spec.getNeedDisablePackageCache();
  }

  @SpecValue(field = "reload_by_factory_reset")
  public boolean reloadByFactoryReset() {
    return spec.getReloadByFactoryReset();
  }
}
