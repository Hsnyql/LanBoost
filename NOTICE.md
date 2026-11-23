# LanBoost licensing and source notices

Copyright (C) 2026 yuqielu

## Original LanBoost implementation

The original LanBoost implementation is free software: you can redistribute it
and/or modify it under the terms of the GNU General Public License, version 3
only, as published by the Free Software Foundation (SPDX: GPL-3.0-only).

LanBoost is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
A PARTICULAR PURPOSE. See LICENSE for the complete GNU GPL version 3 terms.

Project and corresponding source: https://github.com/Hsnyql/LanBoost
Versioned source archives are distributed alongside APKs in GitHub Releases.
Access follows the repository's visibility; anyone redistributing an APK must
also provide its recipients with the corresponding source under GPLv3.

## Third-party components

Third-party libraries, their notices, and the Gradle wrapper retain their
respective licenses and copyright notices. This project's license grant does
not replace those licenses. Dependency notices are in
app/src/main/assets/THIRD_PARTY_NOTICES.txt and the APK's assets.

## XboxDownload reference material

Reference project: https://github.com/skydevil88/XboxDownload
Reference revision: 9acd0cde323636dd54bd0bfcef2297bed306a314

- Domain mappings and alternate-CDN block lists in Rules.java were assembled
  from XboxDownload/Services/DnsMappingGenerator.cs and
  XboxDownload/Resources/Akamai.txt.
- Default benchmark URLs in Profile.java were selected from
  XboxDownload/Models/SpeedTest/SpeedTestDataBuilder.cs.
- NodeCatalogFetcher downloads the reference project's IP/IP.*.txt lists at
  the user's request. Those complete lists are not bundled in the APK.

The Android UI, DNS protocol handling, throughput measurement, and Root port
forwarding use the LanBoost Java implementation. The reference project is not
an application dependency, and this project does not claim its endorsement.

A license or separate permission for the referenced XboxDownload material has
not been established. The GPL grant above covers only material the LanBoost
copyright holder has the right to license; it does not grant rights to
third-party code, rule collections, or datasets on their owners' behalf.
Retain these source notices and assess that material's permissions separately.
