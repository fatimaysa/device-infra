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

package com.google.devtools.atsconsole.result.report;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.devtools.atsconsole.result.proto.ReportProto.Attribute;
import com.google.devtools.atsconsole.result.proto.ReportProto.BuildInfo;
import com.google.devtools.atsconsole.result.proto.ReportProto.Module;
import com.google.devtools.atsconsole.result.proto.ReportProto.Result;
import com.google.devtools.atsconsole.result.proto.ReportProto.Summary;
import com.google.devtools.atsconsole.result.report.CompatibilityReportMerger.ParseResult;
import com.google.devtools.atsconsole.result.report.MoblyReportParser.MoblyReportInfo;
import com.google.devtools.atsconsole.util.TestRunfilesUtil;
import com.google.inject.Guice;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CompatibilityReportMergerTest {

  private static final String CTS_TEST_RESULT_XML =
      TestRunfilesUtil.getRunfilesLocation("result/report/testdata/xml/cts_test_result.xml");

  private static final String CTS_TEST_RESULT_XML_2 =
      TestRunfilesUtil.getRunfilesLocation("result/report/testdata/xml/cts_test_result_2.xml");

  private static final String MOBLY_TEST_SUMMARY_FILE_1 =
      TestRunfilesUtil.getRunfilesLocation("result/report/testdata/mobly/pass/test_summary.yaml");

  private static final String MOBLY_RESULT_ATTR_FILE_1 =
      TestRunfilesUtil.getRunfilesLocation(
          "result/report/testdata/mobly/pass/result_attrs.textproto");

  private static final String MOBLY_BUILD_ATTR_FILE_1 =
      TestRunfilesUtil.getRunfilesLocation(
          "result/report/testdata/mobly/pass/build_attrs.textproto");

  private static final String MOBLY_TEST_SUMMARY_FILE_2 =
      TestRunfilesUtil.getRunfilesLocation("result/report/testdata/mobly/fail/test_summary.yaml");

  private static final String MOBLY_RESULT_ATTR_FILE_2 =
      TestRunfilesUtil.getRunfilesLocation(
          "result/report/testdata/mobly/fail/result_attrs.textproto");

  private static final String MOBLY_BUILD_ATTR_FILE_2 =
      TestRunfilesUtil.getRunfilesLocation(
          "result/report/testdata/mobly/fail/build_attrs.textproto");

  private static final String DEVICE_BUILD_FINGERPRINT =
      "google/bramble/bramble:UpsideDownCake/UP1A.220722.002/8859461:userdebug/dev-keys";

  @Inject private CompatibilityReportMerger reportMerger;

  @Before
  public void setUp() {
    Guice.createInjector(new TestModule()).injectMembers(this);
  }

  @Test
  public void parseXmlReports() throws Exception {
    List<ParseResult> res =
        reportMerger.parseXmlReports(
            ImmutableList.of(Paths.get(CTS_TEST_RESULT_XML), Paths.get(CTS_TEST_RESULT_XML_2)));

    assertThat(res).hasSize(2);
    assertThat(res.get(0).report().get().getModuleInfoList()).hasSize(2);
    assertThat(res.get(1).report().get().getModuleInfoList()).hasSize(3);
  }

  @Test
  public void mergeXmlReports() throws Exception {
    Optional<Result> res =
        reportMerger.mergeXmlReports(
            ImmutableList.of(Paths.get(CTS_TEST_RESULT_XML_2), Paths.get(CTS_TEST_RESULT_XML)));

    assertThat(res).isPresent();
    Result result = res.get();

    assertThat(result.getAttributeList())
        .containsExactly(
            Attribute.newBuilder().setKey("start").setValue("1678951330449").build(),
            Attribute.newBuilder().setKey("end").setValue("1680772548260").build(),
            Attribute.newBuilder()
                .setKey("start_display")
                .setValue("Thu Mar 16 00:22:10 PDT 2023")
                .build(),
            Attribute.newBuilder()
                .setKey("end_display")
                .setValue("Thu Apr 06 17:15:48 CST 2023")
                .build(),
            Attribute.newBuilder()
                .setKey("devices")
                .setValue("12241FDD4002Z6,12241FDD4002Z6")
                .build());
    assertThat(result.getBuild())
        .isEqualTo(
            BuildInfo.newBuilder()
                .setBuildFingerprint(
                    "google/bramble/bramble:UpsideDownCake/UP1A.220722.002/8859461:userdebug/dev-keys")
                .addAttribute(
                    Attribute.newBuilder()
                        .setKey("device_kernel_info")
                        .setValue(
                            "Linux localhost 5.10.149-android13-4-693040-g6422af733678-ab9739629 #1"
                                + " SMP PREEMPT Fri Mar 10 01:44:38 UTC 2023 aarch64 Toybox"))
                .addAttribute(
                    Attribute.newBuilder()
                        .setKey("build_fingerprint")
                        .setValue(
                            "google/bramble/bramble:UpsideDownCake/UP1A.220722.002/8859461:userdebug/dev-keys"))
                .build());
    assertThat(result.getRunHistory().getRunCount()).isEqualTo(4);
    assertThat(result.getSummary())
        .isEqualTo(
            Summary.newBuilder()
                .setPassed(20)
                .setFailed(2)
                .setModulesDone(3)
                .setModulesTotal(3)
                .build());
    assertThat(result.getModuleInfoCount()).isEqualTo(3);

    // Assert module mergion
    Module module1 = result.getModuleInfoList().get(0);
    assertThat(module1.getName()).isEqualTo("Module1");
    assertThat(module1.getRuntimeMillis()).isEqualTo(7495 + 7495);
    assertThat(module1.getPassed()).isEqualTo(6);
    assertThat(module1.getTotalTests()).isEqualTo(8);
    assertThat(module1.getTestCaseCount()).isEqualTo(2);
    assertThat(module1.getTestCase(0).getTestCount()).isEqualTo(4);
    assertThat(module1.getTestCase(1).getTestCount()).isEqualTo(4);
  }

  @Test
  public void parseMoblyReports() throws Exception {
    List<ParseResult> res =
        reportMerger.parseMoblyReports(
            ImmutableList.of(
                MoblyReportInfo.of(
                    "mobly-package-1",
                    Paths.get(MOBLY_TEST_SUMMARY_FILE_1),
                    Paths.get(MOBLY_RESULT_ATTR_FILE_1),
                    DEVICE_BUILD_FINGERPRINT,
                    Paths.get(MOBLY_BUILD_ATTR_FILE_1)),
                MoblyReportInfo.of(
                    "mobly-package-2",
                    Paths.get(MOBLY_TEST_SUMMARY_FILE_2),
                    Paths.get(MOBLY_RESULT_ATTR_FILE_2),
                    DEVICE_BUILD_FINGERPRINT,
                    Paths.get(MOBLY_BUILD_ATTR_FILE_2))));

    assertThat(res).hasSize(2);
    assertThat(res.get(0).report().get().getModuleInfoList()).hasSize(1);
    assertThat(res.get(1).report().get().getModuleInfoList()).hasSize(1);
  }

  @Test
  public void mergeMoblyReports() throws Exception {
    Optional<Result> res =
        reportMerger.mergeMoblyReports(
            ImmutableList.of(
                MoblyReportInfo.of(
                    "mobly-package-1",
                    Paths.get(MOBLY_TEST_SUMMARY_FILE_1),
                    Paths.get(MOBLY_RESULT_ATTR_FILE_1),
                    DEVICE_BUILD_FINGERPRINT,
                    Paths.get(MOBLY_BUILD_ATTR_FILE_1)),
                MoblyReportInfo.of(
                    "mobly-package-2",
                    Paths.get(MOBLY_TEST_SUMMARY_FILE_2),
                    Paths.get(MOBLY_RESULT_ATTR_FILE_2),
                    DEVICE_BUILD_FINGERPRINT,
                    Paths.get(MOBLY_BUILD_ATTR_FILE_2))));

    assertThat(res).isPresent();
    Result result = res.get();

    assertThat(result.getAttributeList())
        .containsExactly(
            Attribute.newBuilder()
                .setKey("result_attr1_key")
                .setValue("result_attr1_value")
                .build(),
            Attribute.newBuilder()
                .setKey("result_attr2_key")
                .setValue("result_attr2_value")
                .build(),
            Attribute.newBuilder().setKey("start").setValue("").build(),
            Attribute.newBuilder().setKey("end").setValue("").build(),
            Attribute.newBuilder().setKey("start_display").setValue("").build(),
            Attribute.newBuilder().setKey("end_display").setValue("").build(),
            Attribute.newBuilder().setKey("devices").setValue("").build());
    assertThat(result.getBuild())
        .isEqualTo(
            BuildInfo.newBuilder()
                .setBuildFingerprint(DEVICE_BUILD_FINGERPRINT)
                .addAttribute(
                    Attribute.newBuilder().setKey("build_attr1_key").setValue("build_attr1_value"))
                .addAttribute(
                    Attribute.newBuilder().setKey("build_attr2_key").setValue("build_attr2_value"))
                .build());
    assertThat(result.getSummary())
        .isEqualTo(
            Summary.newBuilder()
                .setPassed(1)
                .setFailed(2)
                .setModulesDone(2)
                .setModulesTotal(2)
                .build());
    assertThat(result.getModuleInfoCount()).isEqualTo(2);
  }
}
