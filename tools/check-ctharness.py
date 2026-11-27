#!/usr/bin/env python3
"""Check fork identity and Vietnamese resource contracts without an SDK."""

from collections import Counter
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"
FORMAT = re.compile(
    r"%(?:\d+\$)?[-#+ 0,(<]*\d*(?:\.\d+)?(?:[tT][a-zA-Z]|[bBhHsScCdoxXeEfgGaAn%])"
)


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


def strings(path):
    elements = ET.parse(path).getroot().findall("string")
    names = [element.attrib["name"] for element in elements]
    if len(names) != len(set(names)):
        raise AssertionError(f"Duplicate string names in {path.relative_to(ROOT)}")
    return {element.attrib["name"]: element for element in elements}


class ForkContractTest(unittest.TestCase):
    def test_application_ids_and_labels(self):
        build = read("app/build.gradle.kts")
        self.assertRegex(
            build,
            r'applicationId\s*=\s*if \(tianXuanDevBuild\) '
            r'"com\.ctharness\.app\.dev" else "com\.ctharness\.app"',
        )
        labels = re.findall(r'manifestPlaceholders\["appLabel"\]\s*=\s*(.+)', build)
        self.assertTrue(labels)
        for label in labels:
            self.assertEqual(
                'if (tianXuanDevBuild) "CTHarness Dev" else "CTHarness"', label
            )
        self.assertIn('applicationIdSuffix = ".debug"', build)
        self.assertIn('"ctharness-v${appVersionName}-${variant.name}.apk"', build)

    def test_native_namespaces_are_preserved(self):
        self.assertIn('namespace = "top.wkbin.tianxuan"', read("app/build.gradle.kts"))
        self.assertIn("Java_top_wkbin_tianxuan", read("app/src/main/cpp/pty_native.c"))

    def test_locale_is_advertised_and_selectable(self):
        locales = ET.parse(ROOT / "app/src/main/res/xml/tianxuan_locales_config.xml").getroot()
        self.assertEqual(
            {"vi", "en", "zh-CN"}, {item.attrib[ANDROID + "name"] for item in locales}
        )
        picker = read(
            "feature/settings/src/main/java/top/wkbin/tianxuan/ui/settings/"
            "AppearanceSettingsScreen.kt"
        )
        self.assertIn('LocaleListCompat.forLanguageTags("vi")', picker)
        self.assertIn('appLanguage == "vi"', picker)

    def test_default_language_runs_after_appcompat_restore(self):
        activity = read("app/src/main/java/top/wkbin/tianxuan/MainActivity.kt")
        self.assertLess(
            activity.index("super.onCreate(savedInstanceState)"),
            activity.index("initializeAppLanguage(this)"),
        )
        self.assertLess(activity.index("initializeAppLanguage(this)"), activity.index("setContent {"))

    def test_update_source_belongs_to_fork(self):
        asset = read("app/src/main/assets/update_source.properties")
        self.assertIn("update.repo=mrtaix/tianxuan", asset)
        self.assertIn(
            "update.manifestUrl=https://api.github.com/repos/mrtaix/tianxuan/releases/latest",
            asset,
        )
        self.assertNotIn("124.222.37.253", asset)
        config = read(
            "core/network/src/main/java/top/wkbin/tianxuan/core/network/UpdateSourceConfig.kt"
        )
        self.assertIn('DEFAULT_REPO = "mrtaix/tianxuan"', config)

    def test_updater_rejects_other_application_packages(self):
        updater = read(
            "core/network/src/main/java/top/wkbin/tianxuan/core/network/AppUpdateManager.kt"
        )
        self.assertIn("getPackageArchiveInfo(apkFile.absolutePath, 0)", updater)
        self.assertIn("require(archivePackage == context.packageName)", updater)
        self.assertLess(updater.index("require(archivePackage"), updater.index("FileProvider.getUriForFile"))

    def test_navigation_respects_architecture_size_ratchet(self):
        import json

        path = (
            "feature/navigation/src/main/java/top/wkbin/tianxuan/ui/navigation/"
            "TianXuanNavHost.kt"
        )
        baseline = json.loads(read(".architecture-baseline.json"))
        for path in (
            path,
            "runtime/src/main/java/top/wkbin/tianxuan/runtime/privilege/PrivilegeManager.kt",
            "harness/src/main/java/top/wkbin/tianxuan/harness/HarnessLoop.kt",
            "feature/settings/src/main/java/top/wkbin/tianxuan/ui/settings/"
            "AppearanceSettingsScreen.kt",
        ):
            with self.subTest(file=path):
                self.assertLessEqual(len(read(path).splitlines()), baseline["files"][path])

    def test_app_headers_do_not_render_the_logo(self):
        headers = {
            "feature/components/src/main/java/top/wkbin/tianxuan/ui/components/"
            "RuntimeComponents.kt": "fun RuntimeTopBar(",
            "feature/chat/src/main/java/top/wkbin/tianxuan/ui/chat/ChatTopBar.kt": "fun ChatTopBar(",
            "feature/chat/src/main/java/top/wkbin/tianxuan/ui/chat/SessionsPaneContent.kt":
                "private fun SessionsPaneHeader(",
        }
        for path, marker in headers.items():
            with self.subTest(header=path):
                source = read(path)
                start = source.index(marker)
                body = source[start:]
                declaration = body.find("\nfun TianXuanBrandBadge(")
                if declaration >= 0:
                    body = body[:declaration]
                self.assertNotIn("TianXuanBrandBadge(", body)

    def test_onboarding_screen_has_no_hardcoded_chinese(self):
        screen = read(
            "feature/onboarding/src/main/java/top/wkbin/tianxuan/ui/onboarding/"
            "OnboardingScreen.kt"
        )
        code = re.sub(r"(?m)^\s*//.*$", "", screen)
        literals = re.findall(r'"(?:\\.|[^"\\])*"', code)
        self.assertFalse(
            [value for value in literals if re.search(r"[\u3400-\u9fff]", value)],
            "Onboarding screen still contains hardcoded Chinese literals",
        )
        for locale in ("values", "values-en", "values-vi"):
            with self.subTest(locale=locale):
                resource = strings(
                    ROOT / "feature/onboarding/src/main/res" / locale / "strings.xml"
                )
                for key in (
                    "onboarding_import_json",
                    "onboarding_provider_preset_short",
                    "onboarding_switch",
                    "onboarding_base_url_hint",
                    "onboarding_api_key_credential",
                    "onboarding_filled",
                    "onboarding_model_hint",
                    "onboarding_candidate_models",
                    "onboarding_import_dialog_title",
                    "onboarding_paste_clipboard",
                    "onboarding_choose_file",
                    "onboarding_paste_hint",
                    "onboarding_clipboard_empty",
                    "onboarding_paste_action",
                    "onboarding_choose_json",
                    "onboarding_loaded_chars",
                    "onboarding_load",
                ):
                    self.assertIn(key, resource)

    def test_terminology_and_offline_translation_target(self):
        for locale in ("values", "values-en", "values-vi"):
            with self.subTest(locale=locale):
                resource = read(f"feature/onboarding/src/main/res/{locale}/strings.xml")
                self.assertNotIn("tác nhân AI", resource)
                self.assertNotIn("không gian làm việc", resource)
        vi = read("feature/onboarding/src/main/res/values-vi/strings.xml")
        self.assertIn("Không gian", vi)
        # AI Agent terminology is applied wherever the old phrase existed.
        for locale in ("values", "values-en", "values-vi"):
            for path in (
                "feature/chat/src/main/res/values-vi/strings.xml",
                "feature/custom_iteration/src/main/res/values-vi/strings.xml",
                "feature/components/src/main/res/values-vi/strings.xml",
            ):
                self.assertNotIn("tác nhân AI", read(path))

        manager = read(
            "core/common/src/main/java/top/wkbin/tianxuan/core/common/translation/"
            "TranslationManager.kt"
        )
        self.assertIn("setTargetLanguage(TranslateLanguage.VIETNAMESE)", manager)
        self.assertIn("TranslateRemoteModel.Builder(TranslateLanguage.VIETNAMESE)", manager)
        self.assertNotIn("TranslateLanguage.CHINESE", manager)
        card = read(
            "feature/settings/src/main/java/top/wkbin/tianxuan/ui/settings/"
            "TranslationModelCard.kt"
        )
        self.assertIn("settings_translation_language_pair", card)
        self.assertNotIn('"英语 (en) → 简体中文 (zh-CN)"', card)

    def test_heavy_attachments_queue_recovery_and_shizuku_guidance(self):
        attachment = read(
            "feature/chat/src/main/java/top/wkbin/tianxuan/ui/chat/ChatAttachment.kt"
        )
        self.assertIn("MAX_ATTACHMENT_BYTES = 500L * 1024 * 1024", attachment)
        self.assertNotIn(
            "maxBytes = BoundedStreamCopy.DEFAULT_MAX_ATTACHMENT_BYTES", attachment
        )

        loop = read(
            "harness/src/main/java/top/wkbin/tianxuan/harness/HarnessLoop.kt"
        )
        self.assertIn("block: suspend () -> RunResult,\n    ): Boolean {", loop)
        self.assertIn("agentTaskStateMachine.markRecovering(taskId", loop)
        self.assertIn("val launched = launchSessionJobLocked(", loop)
        self.assertIn("排队消息未能启动，请重新发送", loop)

        privilege = read(
            "runtime/src/main/java/top/wkbin/tianxuan/runtime/privilege/"
            "PrivilegeManager.kt"
        )
        self.assertIn("awaitShizukuBinder()", privilege)
        self.assertNotIn("请在 Shizuku 弹窗中允许天玄", privilege)
        self.assertNotIn("请在 Shizuku App 中授予天玄访问权限", privilege)
        self.assertIn("Shizuku App → 应用 → CTHarness", privilege)

    def test_no_fragile_or_retired_link_targets(self):
        assets = read("app/src/main/assets/update_source.properties")
        self.assertIn("update.repo=mrtaix/tianxuan", assets)
        self.assertNotIn("124.222.37.253", assets)
        self.assertNotIn("wkbin/tianxuan", assets)

        sponsors = read(
            "feature/settings/src/main/java/top/wkbin/tianxuan/ui/settings/"
            "SponsorListRepository.kt"
        )
        self.assertIn("raw.githubusercontent.com/mrtaix/tianxuan/", sponsors)
        self.assertNotIn("wkbin/tianxuan", sponsors)

        for locale in ("values", "values-en", "values-vi"):
            with self.subTest(locale=locale):
                legacy = read(f"feature/settings/src/main/res/{locale}/legacy_strings.xml")
                self.assertNotIn("xiaozhe7772222", legacy)
        self.assertNotIn(
            "xiaozhe7772222",
            read(
                "feature/settings/src/main/java/top/wkbin/tianxuan/ui/settings/"
                "SettingsLocalization.kt"
            ),
        )

        installer_scripts = {
            "app/src/main/assets/scripts/setup_android_core.sh": [
                "android-sdk-tools-static-aarch64.zip",
            ],
            "app/src/main/assets/scripts/setup_termux_ndk.sh": [
                "termux-ndk/releases/download/android-ndk/${NDK_ARCHIVE_NAME}",
            ],
            "app/src/main/assets/scripts/setup_jadx.sh": [
                "skylot/jadx/releases/download/v${JADX_VERSION}/jadx-${JADX_VERSION}.zip",
            ],
        }
        retired_proxies = (
            "ghfast.top", "ghproxy.net", "gh.llkk.cc", "gh-proxy.com",
            "mirror.ghproxy.com",
        )
        for path, artifacts in installer_scripts.items():
            with self.subTest(script=path):
                script = read(path)
                for proxy in retired_proxies:
                    self.assertNotIn(proxy, script)
                for artifact in artifacts:
                    self.assertIn(artifact, script)
                self.assertIn("https://github.com/", script)

    def test_iteration_screen_and_chat_title_use_resources(self):
        screen = read(
            "feature/custom_iteration/src/main/java/top/wkbin/tianxuan/ui/iteration/"
            "CustomIterationScreen.kt"
        )
        code = re.sub(r"(?m)^\s*//.*$", "", screen)
        literals = re.findall(r'"(?:\\.|[^"\\])*"', code)
        self.assertFalse(
            [literal for literal in literals if re.search(r"[\u3400-\u9fff]", literal)],
            "Iteration screen still contains hardcoded Chinese UI strings",
        )
        self.assertIn("stringResource(R.string.iteration_title)", screen)
        self.assertIn("stringResource(R.string.iteration_preparing)", screen)
        navigation = read(
            "feature/navigation/src/main/java/top/wkbin/tianxuan/ui/navigation/"
            "TianXuanNavHost.kt"
        )
        self.assertIn("R.string.iteration_chat_title", navigation)
        self.assertNotIn('HealingTask("🚀 自定义迭代"', navigation)

    def test_iteration_bootstrap_localizes_the_fork_prompt(self):
        bootstrap = read(
            "feature/custom_iteration/src/main/java/top/wkbin/tianxuan/iteration/engine/"
            "CustomIterationBootstrap.kt"
        )
        self.assertIn('TIANXUAN_OFFICIAL_REPO: String = "https://github.com/mrtaix/tianxuan"', bootstrap)
        self.assertIn("R.string.iteration_bootstrap_prompt", bootstrap)
        for locale in ("values", "values-en", "values-vi"):
            with self.subTest(locale=locale):
                resource = strings(
                    ROOT / "feature/custom_iteration/src/main/res" / locale / "strings.xml"
                )
                self.assertIn("iteration_bootstrap_prompt", resource)
                prompt = "".join(resource["iteration_bootstrap_prompt"].itertext())
                for token in (
                    "CTHarness", "com.ctharness.app.dev", "%1$s", "%2$s",
                    "git clone", "gh auth login",
                    "gh workflow run tianxuandev-build.yml",
                    "gh run watch", "--exit-status", "gh run download",
                    "-n tianxuandev-apk",
                ):
                    self.assertIn(token, prompt)
                self.assertNotIn("top.wkbin.tianxuan", prompt)
                self.assertIn("com.ctharness.app.dev", resource["iteration_ci_description"].text)

    def test_vietnamese_covers_source_strings(self):
        count = 0
        for source_file in sorted(ROOT.glob("**/src/main/res/values/*.xml")):
            if "assets" in source_file.relative_to(ROOT).parts:
                continue
            source = strings(source_file)
            expected = {
                key: value for key, value in source.items()
                if value.attrib.get("translatable") != "false"
            }
            if not expected:
                continue
            target_file = source_file.parent.parent / "values-vi" / source_file.name
            with self.subTest(file=str(source_file.relative_to(ROOT))):
                self.assertTrue(target_file.is_file(), "Vietnamese resource file missing")
                target = strings(target_file)
                self.assertFalse(set(expected) - set(target), "Missing Vietnamese keys")
                self.assertFalse(set(target) - set(source), "Unknown Vietnamese keys")
                for key, original in expected.items():
                    translated = target[key]
                    before = "".join(original.itertext())
                    after = "".join(translated.itertext())
                    if before.strip():
                        self.assertTrue(after.strip(), key)
                    self.assertEqual(
                        Counter(FORMAT.findall(before)), Counter(FORMAT.findall(after)),
                        f"Format placeholders changed: {key}",
                    )
                    self.assertEqual(
                        [(node.tag, node.attrib) for node in original.iter() if node is not original],
                        [(node.tag, node.attrib) for node in translated.iter() if node is not translated],
                        f"XML markup changed: {key}",
                    )
                    self.assertEqual(
                        Counter(re.findall(r"\\[nt]", before)),
                        Counter(re.findall(r"\\[nt]", after)),
                        f"Android line/tab escapes changed: {key}",
                    )
                    self.assertNotRegex(after, r"[\u3400-\u9fff]", key)
                    brand_text = after
                    if "Pictures/TianXuan" in before:
                        brand_text = brand_text.replace("Pictures/TianXuan", "")
                    self.assertNotRegex(brand_text, r"TianXuan|天玄", key)
                    self.assertNotRegex(
                        brand_text, r"(?<![A-Za-z0-9_/.-])ctharness(?![A-Za-z0-9_/.-])", key
                    )
                    self.assertEqual(
                        original.attrib.get("formatted"), translated.attrib.get("formatted"),
                        f"Format flag changed: {key}",
                    )
                count += len(expected)
        self.assertGreater(count, 1700, "Unexpectedly incomplete source inventory")


if __name__ == "__main__":
    unittest.main(verbosity=2)
