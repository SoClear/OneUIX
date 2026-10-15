# Audio eraser QS tile (experimental, One UI 9)

This opt-in feature replaces Samsung's automatic Audio eraser top banner with a normal
OneUIX QS tile. Samsung still owns app detection, audio processing, account checks,
strength, voice focus, and the detail UI. No Instagram/Snapchat APK modification is used.

## Install the test build

The **Audio eraser test APK** Actions workflow runs on `feat/audio-eraser-tile` and
uploads **OneUIX-AudioEraser-Test**. The APK uses the separate application ID
`io.github.cybr47.oneuix.audioeraser`, allowing it to coexist with the normal module.
Its signing key is a debug key; it does not replace an official/release-signed install.

1. Install `OneUIX-AudioEraser-Test.apk` from the successful Actions run.
2. Disable the existing OneUIX module in LSPosed before enabling **OneUIX Audio eraser test**.
   Two copies must not hook SystemUI at the same time. Enable the same relevant scopes,
   including **System UI**, and open the test app once to save its preferences.
3. In the test app, open **System UI → Quick settings → Use an Audio eraser QS tile**.
4. Turn it on and restart SystemUI using the app's existing button (or reboot).
5. Edit the quick panel, add **Audio eraser**, then move/resize it using Samsung's editor.
6. Tap its icon to toggle Audio eraser. Expand/secondary-click or long-press it for
   Samsung's original strength and voice focus panel.

The tile stays in the layout when unavailable. It is greyed out when Samsung reports
no eligible playback or a dimmed mode. A supported app that is paused/silent may be
unavailable, just as it is with Samsung's original detection. An available but switched-off
effect uses the normal inactive tile color and can be turned on. Samsung's IAE apps retain
Samsung's own "no additional settings" behavior.

To restore the automatic banner, disable the option and restart SystemUI. To return to
the existing module, disable the test module, re-enable the existing one, and restart.

## Implementation and compatibility

The service registers an ordinary, toggleable Android `TileService`. Only that exact
component in the configured module application ID is intercepted inside SystemUI.
No command broadcast receiver, elevated app permissions, polling loop, allowlist override,
or system-partition modification is involved.

The hook resolves One UI 9's `AudioEraser`, `CustomTile`, `SQSTileImpl`, and the top-banner
combine transform before installing the bridge. The native AudioEraser instance and its
preferences drive tile updates. Preference callbacks and native `updateMode` calls refresh
existing tile instances. Toggle and expansion call Samsung's own actions on the main thread;
`getDetailAdapter` returns Samsung's original adapter where supported. The top banner's
Audio eraser visibility input is suppressed only after the native controller is captured.
Other top banners and playback monitoring continue normally.

The default is off, including when importing an older preference backup. Firmware without
the expected hook targets logs a compatibility error and keeps the original banner.
The app-only tile stays unavailable if the hook/option is not active.

## Verification

CI runs preference migration/round-trip tests, tile-state lifecycle tests, hook-module lint,
and a full debug APK build. The hook contract was inspected in the supplied SystemUI APK.
A successful build is not a device-runtime test.

On-device checklist:

- Enable the option; the automatic bar disappears without leaving an empty gap.
- Add/move the tile, resize it to a circle, restart SystemUI, and verify placement persists.
- Play Instagram and Snapchat content: the tile becomes available with the active app label.
- Switch off/on, open details, change strength/voice focus, and verify actual audio behavior.
- Pause/leave eligible playback: the tile greys out without disappearing.
- Test landscape, lockscreen QS, switching eligible apps, and another top-banner feature.
- Disable the option and restart SystemUI: Samsung's original banner returns.

Restoring the original Instagram package may help the clone's story-video issue; this
change does not establish or repair the cause of that playback issue.
