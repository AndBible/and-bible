/*
 * HTML4 entity data derived from Apache Commons Text 1.9 EntityArrays.
 * Modified by AndBible contributors: transcribed as portable Kotlin integer data.
 * Source: https://github.com/apache/commons-text/blob/rel/commons-text-1.9/src/main/java/org/apache/commons/text/translate/EntityArrays.java
 *
 * Apache Commons Text
 * Copyright 2014-2020 The Apache Software Foundation
 * 
 * This product includes software developed at
 * The Apache Software Foundation (https://www.apache.org/).
 *
 * 
 *                                  Apache License
 *                            Version 2.0, January 2004
 *                         http://www.apache.org/licenses/
 * 
 *    TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION
 * 
 *    1. Definitions.
 * 
 *       "License" shall mean the terms and conditions for use, reproduction,
 *       and distribution as defined by Sections 1 through 9 of this document.
 * 
 *       "Licensor" shall mean the copyright owner or entity authorized by
 *       the copyright owner that is granting the License.
 * 
 *       "Legal Entity" shall mean the union of the acting entity and all
 *       other entities that control, are controlled by, or are under common
 *       control with that entity. For the purposes of this definition,
 *       "control" means (i) the power, direct or indirect, to cause the
 *       direction or management of such entity, whether by contract or
 *       otherwise, or (ii) ownership of fifty percent (50%) or more of the
 *       outstanding shares, or (iii) beneficial ownership of such entity.
 * 
 *       "You" (or "Your") shall mean an individual or Legal Entity
 *       exercising permissions granted by this License.
 * 
 *       "Source" form shall mean the preferred form for making modifications,
 *       including but not limited to software source code, documentation
 *       source, and configuration files.
 * 
 *       "Object" form shall mean any form resulting from mechanical
 *       transformation or translation of a Source form, including but
 *       not limited to compiled object code, generated documentation,
 *       and conversions to other media types.
 * 
 *       "Work" shall mean the work of authorship, whether in Source or
 *       Object form, made available under the License, as indicated by a
 *       copyright notice that is included in or attached to the work
 *       (an example is provided in the Appendix below).
 * 
 *       "Derivative Works" shall mean any work, whether in Source or Object
 *       form, that is based on (or derived from) the Work and for which the
 *       editorial revisions, annotations, elaborations, or other modifications
 *       represent, as a whole, an original work of authorship. For the purposes
 *       of this License, Derivative Works shall not include works that remain
 *       separable from, or merely link (or bind by name) to the interfaces of,
 *       the Work and Derivative Works thereof.
 * 
 *       "Contribution" shall mean any work of authorship, including
 *       the original version of the Work and any modifications or additions
 *       to that Work or Derivative Works thereof, that is intentionally
 *       submitted to Licensor for inclusion in the Work by the copyright owner
 *       or by an individual or Legal Entity authorized to submit on behalf of
 *       the copyright owner. For the purposes of this definition, "submitted"
 *       means any form of electronic, verbal, or written communication sent
 *       to the Licensor or its representatives, including but not limited to
 *       communication on electronic mailing lists, source code control systems,
 *       and issue tracking systems that are managed by, or on behalf of, the
 *       Licensor for the purpose of discussing and improving the Work, but
 *       excluding communication that is conspicuously marked or otherwise
 *       designated in writing by the copyright owner as "Not a Contribution."
 * 
 *       "Contributor" shall mean Licensor and any individual or Legal Entity
 *       on behalf of whom a Contribution has been received by Licensor and
 *       subsequently incorporated within the Work.
 * 
 *    2. Grant of Copyright License. Subject to the terms and conditions of
 *       this License, each Contributor hereby grants to You a perpetual,
 *       worldwide, non-exclusive, no-charge, royalty-free, irrevocable
 *       copyright license to reproduce, prepare Derivative Works of,
 *       publicly display, publicly perform, sublicense, and distribute the
 *       Work and such Derivative Works in Source or Object form.
 * 
 *    3. Grant of Patent License. Subject to the terms and conditions of
 *       this License, each Contributor hereby grants to You a perpetual,
 *       worldwide, non-exclusive, no-charge, royalty-free, irrevocable
 *       (except as stated in this section) patent license to make, have made,
 *       use, offer to sell, sell, import, and otherwise transfer the Work,
 *       where such license applies only to those patent claims licensable
 *       by such Contributor that are necessarily infringed by their
 *       Contribution(s) alone or by combination of their Contribution(s)
 *       with the Work to which such Contribution(s) was submitted. If You
 *       institute patent litigation against any entity (including a
 *       cross-claim or counterclaim in a lawsuit) alleging that the Work
 *       or a Contribution incorporated within the Work constitutes direct
 *       or contributory patent infringement, then any patent licenses
 *       granted to You under this License for that Work shall terminate
 *       as of the date such litigation is filed.
 * 
 *    4. Redistribution. You may reproduce and distribute copies of the
 *       Work or Derivative Works thereof in any medium, with or without
 *       modifications, and in Source or Object form, provided that You
 *       meet the following conditions:
 * 
 *       (a) You must give any other recipients of the Work or
 *           Derivative Works a copy of this License; and
 * 
 *       (b) You must cause any modified files to carry prominent notices
 *           stating that You changed the files; and
 * 
 *       (c) You must retain, in the Source form of any Derivative Works
 *           that You distribute, all copyright, patent, trademark, and
 *           attribution notices from the Source form of the Work,
 *           excluding those notices that do not pertain to any part of
 *           the Derivative Works; and
 * 
 *       (d) If the Work includes a "NOTICE" text file as part of its
 *           distribution, then any Derivative Works that You distribute must
 *           include a readable copy of the attribution notices contained
 *           within such NOTICE file, excluding those notices that do not
 *           pertain to any part of the Derivative Works, in at least one
 *           of the following places: within a NOTICE text file distributed
 *           as part of the Derivative Works; within the Source form or
 *           documentation, if provided along with the Derivative Works; or,
 *           within a display generated by the Derivative Works, if and
 *           wherever such third-party notices normally appear. The contents
 *           of the NOTICE file are for informational purposes only and
 *           do not modify the License. You may add Your own attribution
 *           notices within Derivative Works that You distribute, alongside
 *           or as an addendum to the NOTICE text from the Work, provided
 *           that such additional attribution notices cannot be construed
 *           as modifying the License.
 * 
 *       You may add Your own copyright statement to Your modifications and
 *       may provide additional or different license terms and conditions
 *       for use, reproduction, or distribution of Your modifications, or
 *       for any such Derivative Works as a whole, provided Your use,
 *       reproduction, and distribution of the Work otherwise complies with
 *       the conditions stated in this License.
 * 
 *    5. Submission of Contributions. Unless You explicitly state otherwise,
 *       any Contribution intentionally submitted for inclusion in the Work
 *       by You to the Licensor shall be under the terms and conditions of
 *       this License, without any additional terms or conditions.
 *       Notwithstanding the above, nothing herein shall supersede or modify
 *       the terms of any separate license agreement you may have executed
 *       with Licensor regarding such Contributions.
 * 
 *    6. Trademarks. This License does not grant permission to use the trade
 *       names, trademarks, service marks, or product names of the Licensor,
 *       except as required for reasonable and customary use in describing the
 *       origin of the Work and reproducing the content of the NOTICE file.
 * 
 *    7. Disclaimer of Warranty. Unless required by applicable law or
 *       agreed to in writing, Licensor provides the Work (and each
 *       Contributor provides its Contributions) on an "AS IS" BASIS,
 *       WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *       implied, including, without limitation, any warranties or conditions
 *       of TITLE, NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A
 *       PARTICULAR PURPOSE. You are solely responsible for determining the
 *       appropriateness of using or redistributing the Work and assume any
 *       risks associated with Your exercise of permissions under this License.
 * 
 *    8. Limitation of Liability. In no event and under no legal theory,
 *       whether in tort (including negligence), contract, or otherwise,
 *       unless required by applicable law (such as deliberate and grossly
 *       negligent acts) or agreed to in writing, shall any Contributor be
 *       liable to You for damages, including any direct, indirect, special,
 *       incidental, or consequential damages of any character arising as a
 *       result of this License or out of the use or inability to use the
 *       Work (including but not limited to damages for loss of goodwill,
 *       work stoppage, computer failure or malfunction, or any and all
 *       other commercial damages or losses), even if such Contributor
 *       has been advised of the possibility of such damages.
 * 
 *    9. Accepting Warranty or Additional Liability. While redistributing
 *       the Work or Derivative Works thereof, You may choose to offer,
 *       and charge a fee for, acceptance of support, warranty, indemnity,
 *       or other liability obligations and/or rights consistent with this
 *       License. However, in accepting such obligations, You may act only
 *       on Your own behalf and on Your sole responsibility, not on behalf
 *       of any other Contributor, and only if You agree to indemnify,
 *       defend, and hold each Contributor harmless for any liability
 *       incurred by, or claims asserted against, such Contributor by reason
 *       of your accepting any such warranty or additional liability.
 * 
 *    END OF TERMS AND CONDITIONS
 * 
 *    APPENDIX: How to apply the Apache License to your work.
 * 
 *       To apply the Apache License to your work, attach the following
 *       boilerplate notice, with the fields enclosed by brackets "[]"
 *       replaced with your own identifying information. (Don't include
 *       the brackets!)  The text should be enclosed in the appropriate
 *       comment syntax for the file format. We also recommend that a
 *       file or class name and description of purpose be included on the
 *       same "printed page" as the copyright notice for easier
 *       identification within third-party archives.
 * 
 *    Copyright [yyyy] [name of copyright owner]
 * 
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 * 
 *        http://www.apache.org/licenses/LICENSE-2.0
 * 
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package net.bible.service.format

internal val htmlEntityCodePoints = mapOf(
    "AElig" to 198,
    "Aacute" to 193,
    "Acirc" to 194,
    "Agrave" to 192,
    "Alpha" to 913,
    "Aring" to 197,
    "Atilde" to 195,
    "Auml" to 196,
    "Beta" to 914,
    "Ccedil" to 199,
    "Chi" to 935,
    "Dagger" to 8225,
    "Delta" to 916,
    "ETH" to 208,
    "Eacute" to 201,
    "Ecirc" to 202,
    "Egrave" to 200,
    "Epsilon" to 917,
    "Eta" to 919,
    "Euml" to 203,
    "Gamma" to 915,
    "Iacute" to 205,
    "Icirc" to 206,
    "Igrave" to 204,
    "Iota" to 921,
    "Iuml" to 207,
    "Kappa" to 922,
    "Lambda" to 923,
    "Mu" to 924,
    "Ntilde" to 209,
    "Nu" to 925,
    "OElig" to 338,
    "Oacute" to 211,
    "Ocirc" to 212,
    "Ograve" to 210,
    "Omega" to 937,
    "Omicron" to 927,
    "Oslash" to 216,
    "Otilde" to 213,
    "Ouml" to 214,
    "Phi" to 934,
    "Pi" to 928,
    "Prime" to 8243,
    "Psi" to 936,
    "Rho" to 929,
    "Scaron" to 352,
    "Sigma" to 931,
    "THORN" to 222,
    "Tau" to 932,
    "Theta" to 920,
    "Uacute" to 218,
    "Ucirc" to 219,
    "Ugrave" to 217,
    "Upsilon" to 933,
    "Uuml" to 220,
    "Xi" to 926,
    "Yacute" to 221,
    "Yuml" to 376,
    "Zeta" to 918,
    "aacute" to 225,
    "acirc" to 226,
    "acute" to 180,
    "aelig" to 230,
    "agrave" to 224,
    "alefsym" to 8501,
    "alpha" to 945,
    "amp" to 38,
    "and" to 8743,
    "ang" to 8736,
    "apos" to 39,
    "aring" to 229,
    "asymp" to 8776,
    "atilde" to 227,
    "auml" to 228,
    "bdquo" to 8222,
    "beta" to 946,
    "brvbar" to 166,
    "bull" to 8226,
    "cap" to 8745,
    "ccedil" to 231,
    "cedil" to 184,
    "cent" to 162,
    "chi" to 967,
    "circ" to 710,
    "clubs" to 9827,
    "cong" to 8773,
    "copy" to 169,
    "crarr" to 8629,
    "cup" to 8746,
    "curren" to 164,
    "dArr" to 8659,
    "dagger" to 8224,
    "darr" to 8595,
    "deg" to 176,
    "delta" to 948,
    "diams" to 9830,
    "divide" to 247,
    "eacute" to 233,
    "ecirc" to 234,
    "egrave" to 232,
    "empty" to 8709,
    "emsp" to 8195,
    "ensp" to 8194,
    "epsilon" to 949,
    "equiv" to 8801,
    "eta" to 951,
    "eth" to 240,
    "euml" to 235,
    "euro" to 8364,
    "exist" to 8707,
    "fnof" to 402,
    "forall" to 8704,
    "frac12" to 189,
    "frac14" to 188,
    "frac34" to 190,
    "frasl" to 8260,
    "gamma" to 947,
    "ge" to 8805,
    "gt" to 62,
    "hArr" to 8660,
    "harr" to 8596,
    "hearts" to 9829,
    "hellip" to 8230,
    "iacute" to 237,
    "icirc" to 238,
    "iexcl" to 161,
    "igrave" to 236,
    "image" to 8465,
    "infin" to 8734,
    "int" to 8747,
    "iota" to 953,
    "iquest" to 191,
    "isin" to 8712,
    "iuml" to 239,
    "kappa" to 954,
    "lArr" to 8656,
    "lambda" to 955,
    "lang" to 9001,
    "laquo" to 171,
    "larr" to 8592,
    "lceil" to 8968,
    "ldquo" to 8220,
    "le" to 8804,
    "lfloor" to 8970,
    "lowast" to 8727,
    "loz" to 9674,
    "lrm" to 8206,
    "lsaquo" to 8249,
    "lsquo" to 8216,
    "lt" to 60,
    "macr" to 175,
    "mdash" to 8212,
    "micro" to 181,
    "middot" to 183,
    "minus" to 8722,
    "mu" to 956,
    "nabla" to 8711,
    "nbsp" to 160,
    "ndash" to 8211,
    "ne" to 8800,
    "ni" to 8715,
    "not" to 172,
    "notin" to 8713,
    "nsub" to 8836,
    "ntilde" to 241,
    "nu" to 957,
    "oacute" to 243,
    "ocirc" to 244,
    "oelig" to 339,
    "ograve" to 242,
    "oline" to 8254,
    "omega" to 969,
    "omicron" to 959,
    "oplus" to 8853,
    "or" to 8744,
    "ordf" to 170,
    "ordm" to 186,
    "oslash" to 248,
    "otilde" to 245,
    "otimes" to 8855,
    "ouml" to 246,
    "para" to 182,
    "part" to 8706,
    "permil" to 8240,
    "perp" to 8869,
    "phi" to 966,
    "pi" to 960,
    "piv" to 982,
    "plusmn" to 177,
    "pound" to 163,
    "prime" to 8242,
    "prod" to 8719,
    "prop" to 8733,
    "psi" to 968,
    "quot" to 34,
    "rArr" to 8658,
    "radic" to 8730,
    "rang" to 9002,
    "raquo" to 187,
    "rarr" to 8594,
    "rceil" to 8969,
    "rdquo" to 8221,
    "real" to 8476,
    "reg" to 174,
    "rfloor" to 8971,
    "rho" to 961,
    "rlm" to 8207,
    "rsaquo" to 8250,
    "rsquo" to 8217,
    "sbquo" to 8218,
    "scaron" to 353,
    "sdot" to 8901,
    "sect" to 167,
    "shy" to 173,
    "sigma" to 963,
    "sigmaf" to 962,
    "sim" to 8764,
    "spades" to 9824,
    "sub" to 8834,
    "sube" to 8838,
    "sum" to 8721,
    "sup1" to 185,
    "sup2" to 178,
    "sup3" to 179,
    "sup" to 8835,
    "supe" to 8839,
    "szlig" to 223,
    "tau" to 964,
    "there4" to 8756,
    "theta" to 952,
    "thetasym" to 977,
    "thinsp" to 8201,
    "thorn" to 254,
    "tilde" to 732,
    "times" to 215,
    "trade" to 8482,
    "uArr" to 8657,
    "uacute" to 250,
    "uarr" to 8593,
    "ucirc" to 251,
    "ugrave" to 249,
    "uml" to 168,
    "upsih" to 978,
    "upsilon" to 965,
    "uuml" to 252,
    "weierp" to 8472,
    "xi" to 958,
    "yacute" to 253,
    "yen" to 165,
    "yuml" to 255,
    "zeta" to 950,
    "zwj" to 8205,
    "zwnj" to 8204,
)
