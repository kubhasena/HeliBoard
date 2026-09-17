# Vidyullekha

Vidyullekha is a privacy-conscious and customizable open-source keyboard.
It is a fork of [HeliBoard](https://github.com/HeliBorg/HeliBoard), which is based on AOSP / OpenBoard.
Does not use internet permission, and thus is 100% offline.

Android package name: `vidyullekha.keyboard`

## Table of Contents

- [Features](#features)
- [Links](#links)
- [License](#license)
- [Credits](#credits)

# Features

- Add dictionaries for suggestions and spell check
  - build your own, or get them [here](https://codeberg.org/Helium314/aosp-dictionaries#dictionaries) (quality may vary)
  - additional dictionaries for emojis or scientific symbols can be used to provide suggestions (similar to "emoji search")
  - note that for Korean layouts, suggestions only work using [this dictionary](https://github.com/openboard-team/openboard/commit/83fca9533c03b9fecc009fc632577226bbd6301f), the tools in the dictionary repository are not able to create working dictionaries
- Customize keyboard themes (style, colors and background image)
- Emoji search (inline and separate, requires [emoji dictionary](https://codeberg.org/Helium314/aosp-dictionaries))
  - can follow the system's day/night setting on Android 10+ (and on some versions of Android 9)
  - can follow dynamic colors for Android 12+
- Customize keyboard [layouts](layouts.md) (only available when disabling *use system languages*)
- Customize special layouts, like symbols, number, or functional key layout
- Multilingual typing
- Glide typing (*only with closed source library*)
  - library not included in the app, as there is no compatible open source library available
  - can be extracted from GApps packages ("*swypelibs*"), or downloaded [here](https://github.com/erkserkserks/openboard/tree/46fdf2b550035ca69299ce312fa158e7ade36967/app/src/main/jniLibs) (click on the file and then "raw" or the tiny download button)
- Clipboard history
- One-handed mode
- Split keyboard
- Number pad
- Backup and restore your settings and learned word / history data

For [FAQ](https://github.com/HeliBorg/HeliBoard/wiki/FAQ), [hidden features](https://github.com/HeliBorg/HeliBoard/wiki/9.-Hidden-features) and more information about the app and features, see the upstream HeliBoard [wiki](https://github.com/HeliBorg/HeliBoard/wiki).

# Links

- [Layout documentation](layouts.md)
- [Dictionaries](https://codeberg.org/Helium314/aosp-dictionaries)
- [swipe-o-scope](https://codeberg.org/eclexic/swipe-o-scope) for visualizing gesture data



# License

Vidyullekha (as a fork of HeliBoard / OpenBoard) is licensed under GNU General Public License v3.0.

> Permissions of this strong copyleft license are conditioned on making available complete source code of licensed works and modifications, which include larger works using a licensed work, under the same license. Copyright and license notices must be preserved. Contributors provide an express grant of patent rights.

See repo's [LICENSE](/LICENSE) file.

Since the app is based on Apache 2.0 licensed AOSP Keyboard, an [Apache 2.0](LICENSE-Apache-2.0) license file is provided.
The icon is licensed under [Creative Commons BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/). A [license file](LICENSE-CC-BY-SA-4.0) is also included.

# Credits

- Icon by [Fabian OvrWrt](https://github.com/FabianOvrWrt) with contributions from [The Eclectic Dyslexic](https://github.com/the-eclectic-dyslexic)
- [HeliBoard](https://github.com/HeliBorg/HeliBoard)
- [OpenBoard](https://github.com/openboard-team/openboard)
- [AOSP Keyboard](https://android.googlesource.com/platform/packages/inputmethods/LatinIME/)
- [LineageOS](https://review.lineageos.org/admin/repos/LineageOS/android_packages_inputmethods_LatinIME)
- [Simple Keyboard](https://github.com/rkkr/simple-keyboard)
- [Indic Keyboard](https://gitlab.com/indicproject/indic-keyboard)
- [FlorisBoard](https://github.com/florisboard/florisboard/)
- HeliBoard [contributors](https://github.com/HeliBorg/HeliBoard/graphs/contributors)

