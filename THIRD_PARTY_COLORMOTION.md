# ColorMotion

The panel animation feature (`hook/src/main/java/dev/colormotion`, `hook/src/main/java/de/robv/android/xposed`,
`cm-native-api`, `cm-native-bridge`) is derived from **ColorMotion** by juccul,
<https://github.com/juccul/ColorMotion>, licensed under the GNU General Public License v3.0
(see `licenses/ColorMotion-GPL-3.0.txt`).

GPL v3 section 13 allows combining it with this AGPL v3 project. The combined work is
distributed under AGPL v3; the ColorMotion parts remain under their original copyright.

Changes made for OneUIX:
- The module entry was replaced by `dev.colormotion.Entry.install`, called from OneUIX's SystemUI hook.
- A small compatibility layer in `de.robv.android.xposed` maps ColorMotion's legacy hook API onto libxposed.
- The Audio Eraser banner filter was dropped; OneUIX has its own.
