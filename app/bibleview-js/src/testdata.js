/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
import Color from "color";

export let testData, testBookmarks, testBookmarkLabels;

if(process.env.NODE_ENV === "development") {
    testData = [
        `
    <div><title type="x-gen">2 Thessalonians 2:2</title><verse verseOrdinal=1 osisID="2Thess.2.2">2:2 Ver. 2. <p /><hi type="bold">That ye be not soon shaken in mind, &amp;c.]</hi><p /> Or "from your mind or sense", as the Vulgate Latin version; or "from the solidity of sense", as the Arabic version; that is, from what they had received in their minds, and was their sense and judgment, and which they had embraced as articles of faith; that they would not be like a wave of the sea, tossed to and fro with every wind of doctrine; or be moved from the hope of the Gospel, from any fundamental article of it, and from that which respects the second coming of Christ particularly; and especially, that they would not be quickly and easily moved from it; see <reference osisRef="Gal.1.6">Ga 1:6</reference>  <p /><hi type="bold">or be troubled;</hi><p />thrown into consternation and surprise, for though the coming of Christ will not be terrible to saints, as it will be to sinners; yet there is something in it that is awful and solemn, and fills with concern; and to be told of it as at that instant might be surprising and shocking: the several ways in which their minds might be troubled and distressed with such an account are enumerated by the apostle, that they might guard against them, and not be imposed upon by them:  <p /><hi type="bold">neither by spirit;</hi><p />by a prophetic spirit, by pretensions to a revelation from the Spirit, fixing the precise time of Christ's coming, which should not be heeded or attended to; since his coming will be as a thief in the night:  <p /><hi type="bold">nor by word:</hi><p />by reason and a show of it, by arguments drawn from it, which may carry in them a show of probability; by enticing words of man's wisdom; by arithmetical or astronomical calculations; or by pretensions to a word, a tradition of Christ or his apostles, as if they had received it "viva voce", by word of mouth from any of them:  <p /><hi type="bold">nor by letter, as from us;</hi><p />by forging a letter and counterfeiting their hands, for such practices began to be used very early; spurious epistles of the Apostle Paul were carried about, which obliged him to take a method whereby his genuine letters might be known; see <reference osisRef="2Thess.3.17-2Thess.3.18">2Th 3:17,18</reference> or he may have respect in this clause to his former epistle, wherein he had said some things concerning the Coming of Christ, which had been either wrongly represented, or not understood; and as if his sense was, that it would be while he and others then living were alive and on the spot: wherefore he would not have them neither give heed to any enthusiastic spirits, nor to any plausible reasonings of men, or unwritten traditions; nor to any letters in his name, or in the name of any of the apostles; nor even to his former letter to them, as though it contained any such thing in it,  <p /><hi type="bold">as that the day of Christ is at hand;</hi><p />or is at this instant just now coming on; as if it would be within that year, in some certain month, and on some certain day in it; which notion the apostle would have them by no means give into, for these reasons, because should Christ not come, as there was no reason to believe he would in so short a time, they would be tempted to disbelieve his coming at all, at least be very indifferent about it; and since if it did not prove true, they might be led to conclude there was nothing true in the Christian doctrine and religion; and besides, such a notion of the speedy coming of Christ would tend to indulge the idle and disorderly persons among them in their sloth and negligence: and now for these, and for the weighty reasons he gives in the next verse, he dissuades them from imbibing such a tenet; for though the coming of Christ is sometimes said to be drawing nigh, and to be quickly, yet so it might be, and not at that instant; besides, such expressions are used with respect to God, with whom a thousand years are as one day, and one day as a thousand years; and because the Gospel times, or times of the Messiah, are the last days, there will be no other dispensation of things until the second coming of Christ; and chiefly they are used to keep up the faith, and awaken the hope and expectation of the saints with respect to it. The Alexandrian copy, and some others, read, "the day of the Lord"; and so the Vulgate Latin version; and accordingly the Syriac and Ethiopic versions, "the day of our Lord".ß</verse></div>
    `,
        `
    <div><title type="x-gen">2 Thessalonians 3</title><div osisID="2Thess.3.0" type="x-gen"><chapter n="3" osisID="2Thess.3" sID="2Thess.3.seID.30798" /> <div sID="gen11657" type="x-p" /> </div><verse osisID="2Thess.3.1" verseOrdinal="30854"><w lemma="strong:G3063" morph="robinson:A-NSN 2">Finally</w>, <w lemma="strong:G80" morph="robinson:N-VPM 4">brethren</w>, <w lemma="strong:G4336" morph="robinson:V-PNM-2P 3">pray</w> <w lemma="strong:G4012" morph="robinson:PREP 5">for</w> <w lemma="strong:G2257" morph="robinson:P-1GP 6">us</w>, <w lemma="strong:G2443" morph="robinson:CONJ 7">that</w> <w lemma="strong:G3588 strong:G3056" morph="robinson:T-NSM robinson:N-NSM 8 9">the word</w> <w lemma="strong:G3588 strong:G2962" morph="robinson:T-GSM robinson:N-GSM 10 11">of the Lord</w> <w lemma="strong:G5143" morph="robinson:V-PAS-3S 12">may have</w> <transChange type="added">free</transChange> <w lemma="strong:G5143" morph="robinson:V-PAS-3S 12">course</w>, <w lemma="strong:G2532" morph="robinson:CONJ 13">and</w> <w lemma="strong:G1392" morph="robinson:V-PPS-3S 14">be glorified</w>, <w lemma="strong:G2532" morph="robinson:CONJ 16">even</w> <w lemma="strong:G2531" morph="robinson:ADV 15">as</w> <transChange type="added">it is</transChange> <w lemma="strong:G4314" morph="robinson:PREP 17">with</w> <w lemma="strong:G5209" morph="robinson:P-2AP 18">you</w>: </verse><verse osisID="2Thess.3.2" verseOrdinal="30855"><w lemma="strong:G2532" morph="robinson:CONJ 1">And</w> <w lemma="strong:G2443" morph="robinson:CONJ 2">that</w> <w lemma="strong:G4506" morph="robinson:V-APS-1P 3">we may be delivered</w> <w lemma="strong:G575" morph="robinson:PREP 4">from</w> <w lemma="strong:G824" morph="robinson:A-GPM 6">unreasonable</w> <w lemma="strong:G2532" morph="robinson:CONJ 7">and</w> <w lemma="strong:G4190" morph="robinson:A-GPM 8">wicked</w> <w lemma="strong:G444" morph="robinson:N-GPM 9">men</w>: <w lemma="strong:G1063" morph="robinson:CONJ 11">for</w> <w lemma="strong:G3956" morph="robinson:A-GPM 12">all</w> <transChange type="added">men</transChange> have <w lemma="strong:G3756" morph="robinson:PRT-N 10">not</w> <w lemma="strong:G3588 strong:G4102" morph="robinson:T-NSF robinson:N-NSF 13 14">faith</w>. </verse><verse osisID="2Thess.3.3" verseOrdinal="30856"><w lemma="strong:G1161" morph="robinson:CONJ 2">But</w> <w lemma="strong:G3588 strong:G2962" morph="robinson:T-NSM robinson:N-NSM 4 5">the Lord</w> <w lemma="strong:G2076" morph="robinson:V-PXI-3S 3">is</w> <w lemma="strong:G4103" morph="robinson:A-NSM 1">faithful</w>, <w lemma="strong:G3739" morph="robinson:R-NSM 6">who</w> <w lemma="strong:G4741" morph="robinson:V-FAI-3S 7">shall stablish</w> <w lemma="strong:G5209" morph="robinson:P-2AP 8">you</w>, <w lemma="strong:G2532" morph="robinson:CONJ 9">and</w> <w lemma="strong:G5442" morph="robinson:V-FAI-3S 10">keep</w> <transChange type="added">you</transChange> <w lemma="strong:G575" morph="robinson:PREP 11">from</w> <w lemma="strong:G4190" morph="robinson:A-GSM 13">evil</w>. </verse><verse osisID="2Thess.3.4" verseOrdinal="30857"><w lemma="strong:G1161" morph="robinson:CONJ 2">And</w> <w lemma="strong:G3982" morph="robinson:V-2RAI-1P 1">we have confidence</w> <w lemma="strong:G1722" morph="robinson:PREP 3">in</w> <w lemma="strong:G2962" morph="robinson:N-DSM 4">the Lord</w> <w lemma="strong:G1909" morph="robinson:PREP 5">touching</w> <w lemma="strong:G5209" morph="robinson:P-2AP 6">you</w>, <w lemma="strong:G3754" morph="robinson:CONJ 7">that</w> <w lemma="strong:G4160" morph="robinson:V-PAI-2P 12">ye</w> <w lemma="strong:G2532" morph="robinson:CONJ 11">both</w> <w lemma="strong:G4160" morph="robinson:V-PAI-2P 12">do</w> <w lemma="strong:G2532" morph="robinson:CONJ 13">and</w> <w lemma="strong:G4160" morph="robinson:V-FAI-2P 14">will do</w> <w lemma="strong:G3739" morph="robinson:R-APN 8">the things which</w> <w lemma="strong:G3853" morph="robinson:V-PAI-1P 9">we command</w> <w lemma="strong:G5213" morph="robinson:P-2DP 10">you</w>. </verse><verse osisID="2Thess.3.5" verseOrdinal="30858"><w lemma="strong:G1161" morph="robinson:CONJ 2">And</w> <w lemma="strong:G2962" morph="robinson:N-NSM 3">the Lord</w> <w lemma="strong:G2720" morph="robinson:V-AAO-3S 4">direct</w> <w lemma="strong:G5216" morph="robinson:P-2GP 5">your</w> <w lemma="strong:G3588 strong:G2588" morph="robinson:T-APF robinson:N-APF 6 7">hearts</w> <w lemma="strong:G1519" morph="robinson:PREP 8">into</w> <w lemma="strong:G3588 strong:G26" morph="robinson:T-ASF robinson:N-ASF 9 10">the love</w> <w lemma="strong:G3588 strong:G2316" morph="robinson:T-GSM robinson:N-GSM 11 12">of God</w>, <w lemma="strong:G2532" morph="robinson:CONJ 13">and</w> <w lemma="strong:G1519" morph="robinson:PREP 14">into</w> <w lemma="strong:G3588 strong:G5281" morph="robinson:T-ASF robinson:N-ASF 15 16">the patient waiting</w> <w lemma="strong:G3588 strong:G5547" morph="robinson:T-GSM robinson:N-GSM 17 18">for Christ</w>. <div eID="gen11657" type="x-p" /></verse><div type="x-milestone" subType="x-preverse" sID="pv5965" /><div sID="gen11658" type="x-p" /> <div type="x-milestone" subType="x-preverse" eID="pv5965" /><verse osisID="2Thess.3.6" verseOrdinal="30859"> <w lemma="strong:G1161" morph="robinson:CONJ 2">Now</w> <w lemma="strong:G3853" morph="robinson:V-PAI-1P 1">we command</w> <w lemma="strong:G5213" morph="robinson:P-2DP 3">you</w>, <w lemma="strong:G80" morph="robinson:N-VPM 4">brethren</w>, <w lemma="strong:G1722" morph="robinson:PREP 5">in</w> <w lemma="strong:G3686" morph="robinson:N-DSN 6">the name</w> <w lemma="strong:G2257" morph="robinson:P-1GP 9">of our</w> <w lemma="strong:G3588 strong:G2962" morph="robinson:T-GSM robinson:N-GSM 7 8">Lord</w> <w lemma="strong:G2424" morph="robinson:N-GSM 10">Jesus</w> <w lemma="strong:G5547" morph="robinson:N-GSM 11">Christ</w>, <w lemma="strong:G4724" morph="robinson:V-PMN 12">that</w> <w lemma="strong:G5209" morph="robinson:P-2AP 13">ye</w> <w lemma="strong:G4724" morph="robinson:V-PMN 12">withdraw yourselves</w> <w lemma="strong:G575" morph="robinson:PREP 14">from</w> <w lemma="strong:G3956" morph="robinson:A-GSM 15">every</w> <w lemma="strong:G80" morph="robinson:N-GSM 16">brother</w> <w lemma="strong:G4043" morph="robinson:V-PAP-GSM 18">that walketh</w> <w lemma="strong:G814" morph="robinson:ADV 17">disorderly</w>, <w lemma="strong:G2532" morph="robinson:CONJ 19">and</w> <w lemma="strong:G3361" morph="robinson:PRT-N 20">not</w> <w lemma="strong:G2596" morph="robinson:PREP 21">after</w> <w lemma="strong:G3588 strong:G3862" morph="robinson:T-ASF robinson:N-ASF 22 23">the tradition</w> <w lemma="strong:G3739" morph="robinson:R-ASF 24">which</w> <w lemma="strong:G3880" morph="robinson:V-2AAI-3S 25">he received</w> <w lemma="strong:G3844" morph="robinson:PREP 26">of</w> <w lemma="strong:G2257" morph="robinson:P-1GP 27">us</w>. </verse><verse osisID="2Thess.3.7" verseOrdinal="30860"><w lemma="strong:G1063" morph="robinson:CONJ 2">For</w> <w lemma="strong:G846" morph="robinson:P-NPM 1">yourselves</w> <w lemma="strong:G1492" morph="robinson:V-RAI-2P 3">know</w> <w lemma="strong:G4459" morph="robinson:ADV 4">how</w> <w lemma="strong:G1163" morph="robinson:V-PQI-3S 5">ye ought</w> <w lemma="strong:G3401" morph="robinson:V-PNN 6">to follow</w> <w lemma="strong:G2248" morph="robinson:P-1AP 7">us</w>: <w lemma="strong:G3754" morph="robinson:CONJ 8">for</w> <w lemma="strong:G812" morph="robinson:V-AAI-1P 10">we behaved</w> <w lemma="strong:G3756" morph="robinson:PRT-N 9">not</w> <w lemma="strong:G812" morph="robinson:V-AAI-1P 10">ourselves disorderly</w> <w lemma="strong:G1722" morph="robinson:PREP 11">among</w> <w lemma="strong:G5213" morph="robinson:P-2DP 12">you</w>; </verse><verse osisID="2Thess.3.8" verseOrdinal="30861"><w lemma="strong:G3761" morph="robinson:ADV 1">Neither</w> <w lemma="strong:G5315" morph="robinson:V-2AAI-1P 4">did we eat</w> <w lemma="strong:G5100" morph="robinson:X-GSM 6">any man’s</w> <w lemma="strong:G740" morph="robinson:N-ASM 3">bread</w> <w lemma="strong:G1432" morph="robinson:ADV 2">for nought</w>; <w lemma="strong:G235" morph="robinson:CONJ 7">but</w> <w lemma="strong:G2038" morph="robinson:V-PNP-NPM 15">wrought</w> <w lemma="strong:G1722" morph="robinson:PREP 8">with</w> <w lemma="strong:G2873" morph="robinson:N-DSM 9">labour</w> <w lemma="strong:G2532" morph="robinson:CONJ 10">and</w> <w lemma="strong:G3449" morph="robinson:N-DSM 11">travail</w> <w lemma="strong:G3571" morph="robinson:N-ASF 12">night</w> <w lemma="strong:G2532" morph="robinson:CONJ 13">and</w> <w lemma="strong:G2250" morph="robinson:N-ASF 14">day</w>, <w lemma="strong:G4314" morph="robinson:PREP 16">that</w> <w lemma="strong:G1912" morph="robinson:V-AAN 19">we might</w> <w lemma="strong:G3361" morph="robinson:PRT-N 18">not</w> <w lemma="strong:G1912" morph="robinson:V-AAN 19">be chargeable to</w> <w lemma="strong:G5100" morph="robinson:X-ASM 20">any</w> <w lemma="strong:G5216" morph="robinson:P-2GP 21">of you</w>: </verse><verse osisID="2Thess.3.9" verseOrdinal="30862"><w lemma="strong:G3756" morph="robinson:PRT-N 1">Not</w> <w lemma="strong:G3754" morph="robinson:CONJ 2">because</w> <w lemma="strong:G2192" morph="robinson:V-PAI-1P 4">we have</w> <w lemma="strong:G3756" morph="robinson:PRT-N 3">not</w> <w lemma="strong:G1849" morph="robinson:N-ASF 5">power</w>, <w lemma="strong:G235" morph="robinson:CONJ 6">but</w> <w lemma="strong:G2443" morph="robinson:CONJ 7">to</w> <w lemma="strong:G1325" morph="robinson:V-2AAS-1P 10">make</w> <w lemma="strong:G1438" morph="robinson:F-3APM 8">ourselves</w> <w lemma="strong:G5179" morph="robinson:N-ASM 9">an ensample</w> <w lemma="strong:G5213" morph="robinson:P-2DP 11">unto you</w> <w lemma="strong:G1519" morph="robinson:PREP 12">to</w> <w lemma="strong:G3401" morph="robinson:V-PNN 14">follow</w> <w lemma="strong:G2248" morph="robinson:P-1AP 15">us</w>. </verse><verse osisID="2Thess.3.10" verseOrdinal="30863"><w lemma="strong:G1063" morph="robinson:CONJ 2">For</w> <w lemma="strong:G2532" morph="robinson:CONJ 1">even</w> <w lemma="strong:G3753" morph="robinson:ADV 3">when</w> <w lemma="strong:G1510" morph="robinson:V-IXI-1P 4">we were</w> <w lemma="strong:G4314" morph="robinson:PREP 5">with</w> <w lemma="strong:G5209" morph="robinson:P-2AP 6">you</w>, <w lemma="strong:G5124" morph="robinson:D-ASN 7">this</w> <w lemma="strong:G3853" morph="robinson:V-IAI-1P 8">we commanded</w> <w lemma="strong:G5213" morph="robinson:P-2DP 9">you</w>, <w lemma="strong:G3754" morph="robinson:CONJ 10">that</w> <w lemma="strong:G1487" morph="robinson:COND 11">if</w> <w lemma="strong:G5100" morph="robinson:X-NSM 12">any</w> <w lemma="strong:G2309" morph="robinson:V-PAI-3S 14">would</w> <w lemma="strong:G3756" morph="robinson:PRT-N 13">not</w> <w lemma="strong:G2038" morph="robinson:V-PNN 15">work</w>, <w lemma="strong:G3366" morph="robinson:CONJ 16">neither</w> <w lemma="strong:G2068" morph="robinson:V-PAM-3S 17">should he eat</w>. </verse><verse osisID="2Thess.3.11" verseOrdinal="30864"><w lemma="strong:G1063" morph="robinson:CONJ 2">For</w> <w lemma="strong:G191" morph="robinson:V-PAI-1P 1">we hear</w> <w lemma="strong:G4043" morph="robinson:V-PAP-APM 4">that there are</w> <w lemma="strong:G5100" morph="robinson:X-APM 3">some</w> <w lemma="strong:G4043" morph="robinson:V-PAP-APM 4">which walk</w> <w lemma="strong:G1722" morph="robinson:PREP 5">among</w> <w lemma="strong:G5213" morph="robinson:P-2DP 6">you</w> <w lemma="strong:G814" morph="robinson:ADV 7">disorderly</w>, <w lemma="strong:G2038" morph="robinson:V-PNP-APM 9">working</w> <w lemma="strong:G3367" morph="robinson:A-ASN 8">not at all</w>, <w lemma="strong:G235" morph="robinson:CONJ 10">but</w> <w lemma="strong:G4020" morph="robinson:V-PNP-APM 11">are busybodies</w>. </verse><verse osisID="2Thess.3.12" verseOrdinal="30865"><w lemma="strong:G1161" morph="robinson:CONJ 2">Now</w> <w lemma="strong:G5108" morph="robinson:D-DPM 3">them that are such</w> <w lemma="strong:G3853" morph="robinson:V-PAI-1P 4">we command</w> <w lemma="strong:G2532" morph="robinson:CONJ 5">and</w> <w lemma="strong:G3870" morph="robinson:V-PAI-1P 6">exhort</w> <w lemma="strong:G1223" morph="robinson:PREP 7">by</w> <w lemma="strong:G2257" morph="robinson:P-1GP 10">our</w> <w lemma="strong:G3588 strong:G2962" morph="robinson:T-GSM robinson:N-GSM 8 9">Lord</w> <w lemma="strong:G2424" morph="robinson:N-GSM 11">Jesus</w> <w lemma="strong:G5547" morph="robinson:N-GSM 12">Christ</w>, <w lemma="strong:G2443" morph="robinson:CONJ 13">that</w> <w lemma="strong:G3326" morph="robinson:PREP 14">with</w> <w lemma="strong:G2271" morph="robinson:N-GSF 15">quietness</w> <w lemma="strong:G2038" morph="robinson:V-PNP-NPM 16">they work</w>, and <w lemma="strong:G2068" morph="robinson:V-PAS-3P 20">eat</w> <w lemma="strong:G1438" morph="robinson:F-3GPM 18">their own</w> <w lemma="strong:G740" morph="robinson:N-ASM 19">bread</w>. </verse><verse osisID="2Thess.3.13" verseOrdinal="30866"><w lemma="strong:G1161" morph="robinson:CONJ 2">But</w> <w lemma="strong:G5210" morph="robinson:P-2NP 1">ye</w>, <w lemma="strong:G80" morph="robinson:N-VPM 3">brethren</w>, <w lemma="strong:G1573" morph="robinson:V-AAS-2P 5">be</w> <w lemma="strong:G3361" morph="robinson:PRT-N 4">not</w> <w lemma="strong:G1573" morph="robinson:V-AAS-2P 5">weary</w> <w lemma="strong:G2569" morph="robinson:V-PAP-NPM 6">in well doing</w>. </verse><verse osisID="2Thess.3.14" verseOrdinal="30867"><w lemma="strong:G1161" morph="robinson:CONJ 2">And</w> <w lemma="strong:G1487" morph="robinson:COND 1">if</w> <w lemma="strong:G5100" morph="robinson:X-NSM 3">any man</w> <w lemma="strong:G5219" morph="robinson:V-PAI-3S 5">obey</w> <w lemma="strong:G3756" morph="robinson:PRT-N 4">not</w> <w lemma="strong:G2257" morph="robinson:P-1GP 8">our</w> <w lemma="strong:G3588 strong:G3056" morph="robinson:T-DSM robinson:N-DSM 6 7">word</w> <w lemma="strong:G1223" morph="robinson:PREP 9">by</w> <w lemma="strong:G3588 strong:G1992" morph="robinson:T-GSF robinson:N-GSF 10 11">this epistle</w>, <w lemma="strong:G4593" morph="robinson:V-PMM-2P 13">note</w> <w lemma="strong:G5126" morph="robinson:D-ASM 12">that man</w>, <w lemma="strong:G2532" morph="robinson:CONJ 14">and</w> <w lemma="strong:G4874" morph="robinson:V-PMM-2P 16">have</w> <w lemma="strong:G3361" morph="robinson:PRT-N 15">no</w> <w lemma="strong:G4874" morph="robinson:V-PMM-2P 16">company with</w> <w lemma="strong:G846" morph="robinson:P-DSM 17">him</w>, <w lemma="strong:G2443" morph="robinson:CONJ 18">that</w> <w lemma="strong:G1788" morph="robinson:V-2APS-3S 19">he may be ashamed</w>. </verse><verse osisID="2Thess.3.15" verseOrdinal="30868"><w lemma="strong:G2532" morph="robinson:CONJ 1">Yet</w> <w lemma="strong:G2233" morph="robinson:V-PNM-2P 5">count</w> <transChange type="added">him</transChange> <w lemma="strong:G3361" morph="robinson:PRT-N 2">not</w> <w lemma="strong:G5613" morph="robinson:ADV 3">as</w> <w lemma="strong:G2190" morph="robinson:A-ASM 4">an enemy</w>, <w lemma="strong:G235" morph="robinson:CONJ 6">but</w> <w lemma="strong:G3560" morph="robinson:V-PAM-2P 7">admonish</w> <transChange type="added">him</transChange> <w lemma="strong:G5613" morph="robinson:ADV 8">as</w> <w lemma="strong:G80" morph="robinson:N-ASM 9">a brother</w>. </verse><verse osisID="2Thess.3.16" verseOrdinal="30869"><w lemma="strong:G1161" morph="robinson:CONJ 2">Now</w> <w lemma="strong:G3588 strong:G2962" morph="robinson:T-NSM robinson:N-NSM 3 4">the Lord</w> <w lemma="strong:G3588 strong:G1515" morph="robinson:T-GSF robinson:N-GSF 5 6">of peace</w> <w lemma="strong:G846" morph="robinson:P-NSM 1">himself</w> <w lemma="strong:G1325" morph="robinson:V-2AAO-3S 7">give</w> <w lemma="strong:G5213" morph="robinson:P-2DP 8">you</w> <w lemma="strong:G3588 strong:G1515" morph="robinson:T-ASF robinson:N-ASF 9 10">peace</w> <w lemma="strong:G1223" morph="robinson:PREP 11">always</w> <w lemma="strong:G1722" morph="robinson:PREP 13">by</w> <w lemma="strong:G3956" morph="robinson:A-DSM 14">all</w> <w lemma="strong:G5158" morph="robinson:N-DSM 15">means</w>. <w lemma="strong:G3588 strong:G2962" morph="robinson:T-NSM robinson:N-NSM 16 17">The Lord</w> <transChange type="added">be</transChange> <w lemma="strong:G3326" morph="robinson:PREP 18">with</w> <w lemma="strong:G5216" morph="robinson:P-2GP 20">you</w> <w lemma="strong:G3956" morph="robinson:A-GPM 19">all</w>. <div eID="gen11658" type="x-p" /></verse><div type="x-milestone" subType="x-preverse" sID="pv5966" /><div sID="gen11659" type="x-p" /> <div type="x-milestone" subType="x-preverse" eID="pv5966" /><verse osisID="2Thess.3.17" verseOrdinal="30870"> <w lemma="strong:G3588 strong:G783" morph="robinson:T-NSM robinson:N-NSM 1 2">The salutation</w> <w lemma="strong:G3972" morph="robinson:N-GSM 6">of Paul</w> <w lemma="strong:G1699" morph="robinson:S-1DSF 4">with mine own</w> <w lemma="strong:G5495" morph="robinson:N-DSF 5">hand</w>, <w lemma="strong:G3739" morph="robinson:R-NSN 7">which</w> <w lemma="strong:G2076" morph="robinson:V-PXI-3S 8">is</w> <w lemma="strong:G4592" morph="robinson:N-NSN 9">the token</w> <w lemma="strong:G1722" morph="robinson:PREP 10">in</w> <w lemma="strong:G3956" morph="robinson:A-DSF 11">every</w> <w lemma="strong:G1992" morph="robinson:N-DSF 12">epistle</w>: <w lemma="strong:G3779" morph="robinson:ADV 13">so</w> <w lemma="strong:G1125" morph="robinson:V-PAI-1S 14">I write</w>. </verse><w lemma="strong:G3588 strong:G5485" morph="robinson:T-NSF robinson:N-NSF 1 2">The grace</w> <w lemma="strong:G2257" morph="robinson:P-1GP 5">of our</w> <w lemma="strong:G3588 strong:G2962" morph="robinson:T-GSM robinson:N-GSM 3 4">Lord</w> <w lemma="strong:G2424" morph="robinson:N-GSM 6">Jesus</w> <w lemma="strong:G5547" morph="robinson:N-GSM 7">Christ</w> <transChange type="added">be</transChange> <w lemma="strong:G3326" morph="robinson:PREP 8">with</w> <w lemma="strong:G5216" morph="robinson:P-2GP 10">you</w> <w lemma="strong:G3956" morph="robinson:A-GPM 9">all</w>. <w lemma="strong:G281" morph="robinson:HEB 11">Amen</w>. <div eID="gen11659" type="x-p" /><div type="x-milestone" subType="x-preverse" sID="pv5967" /><verse osisID="2Thess.3.18" verseOrdinal="30871"><div sID="gen11660" type="section" /><title canonical="false" type="sub">The second <transChange type="added">epistle</transChange> to the Thessalonians was written from Athens. </title><chapter eID="2Thess.3.seID.30798" /><div eID="gen11660" type="section" /><div eID="gen11652" type="majorSection" /><div canonical="true" eID="gen11651" osisID="2Thess" type="book" /></verse></div>
    `,
        `
    <div><title type="x-gen">2 Thessalonians 2</title><div osisID="2Thess.2.0" type="x-gen"><chapter osisID="2Thess.2" sID="gen34214" /> </div><div type="x-milestone" subType="x-preverse" sID="pv6467" /><div sID="gen34215" type="section" /> <title>The Man of Lawlessness</title> <div sID="gen34216" type="paragraph" /> <div type="x-milestone" subType="x-preverse" eID="pv6467" /><verse osisID="2Thess.2.1" verseOrdinal="30836"><w lemma="strong:G1161">Now</w> <w lemma="strong:G5228">concerning</w> <note n="a" osisID="2Thess.2.1!crossReference.a" osisRef="2Thess.2.1" type="crossReference">See <reference osisRef="1Thess.2.19">1 Thess. 2:19</reference></note>the <w lemma="strong:G3952">coming</w> of <w lemma="strong:G2257">our</w> <w lemma="strong:G2962">Lord</w> <w lemma="strong:G2424">Jesus</w> <w lemma="strong:G5547">Christ</w> <w lemma="strong:G2532">and</w> <w lemma="strong:G2257">our</w> <note n="b" osisID="2Thess.2.1!crossReference.b" osisRef="2Thess.2.1" type="crossReference">[<reference osisRef="Matt.24.31">Matt. 24:31</reference>; <reference osisRef="1Thess.4.15-1Thess.4.17">1 Thess. 4:15-17</reference>]</note>being <w lemma="strong:G1997">gathered together</w> <w lemma="strong:G1909">to</w> <w lemma="strong:G846">him</w>, we <w lemma="strong:G2065">ask</w> <w lemma="strong:G5209">you</w>, <w lemma="strong:G80">brothers</w>,<note n="1" osisID="2Thess.2.1!note.1" osisRef="2Thess.2.1" subType="x-gender-neutral" type="translation">Or <hi type="italic"><catchWord>brothers</catchWord> and sisters</hi>; also verses 13, 15</note></verse><verse osisID="2Thess.2.2" verseOrdinal="30837"><w lemma="strong:G1519 strong:G5209 strong:G3361">not</w> to <w lemma="strong:G4531">be</w> <w lemma="strong:G5030">quickly</w> <w lemma="strong:G4531">shaken</w> <w lemma="strong:G575">in</w> <w lemma="strong:G3563">mind</w> <w lemma="strong:G3383">or</w> <note n="c" osisID="2Thess.2.2!crossReference.c" osisRef="2Thess.2.2" type="crossReference"><reference osisRef="Matt.24.6">Matt. 24:6</reference>; <reference osisRef="Mark.13.7">Mark 13:7</reference></note><w lemma="strong:G2360">alarmed</w>, <w lemma="strong:G3383">either</w> <note n="d" osisID="2Thess.2.2!crossReference.d" osisRef="2Thess.2.2" type="crossReference">[<reference osisRef="1John.4.1">1 John 4:1</reference>]</note><w lemma="strong:G1223">by</w> a <w lemma="strong:G4151">spirit</w> <w lemma="strong:G3383 strong:G1223">or</w> a <note n="e" osisID="2Thess.2.2!crossReference.e" osisRef="2Thess.2.2" type="crossReference"><reference osisRef="2Thess.2.15">ver. 15</reference>; [<reference osisRef="1Thess.5.2">1 Thess. 5:2</reference>]</note>spoken <w lemma="strong:G3056">word</w>, <w lemma="strong:G3383 strong:G1223">or</w> <note n="e" osisID="2Thess.2.2!crossReference.e" osisRef="2Thess.2.2" type="crossReference"><reference osisRef="2Thess.2.15">ver. 15</reference>; [<reference osisRef="1Thess.5.2">1 Thess. 5:2</reference>]</note>a <w lemma="strong:G1992">letter</w> seeming to <w lemma="strong:G5613">be</w> <w lemma="strong:G1223">from</w> <w lemma="strong:G2257">us</w>, to the <w lemma="strong:G5613">effect</w> <w lemma="strong:G3754">that</w> <note n="f" osisID="2Thess.2.2!crossReference.f" osisRef="2Thess.2.2" type="crossReference">See <reference osisRef="1Cor.1.8">1 Cor. 1:8</reference></note>the <w lemma="strong:G2250">day</w> of the <w lemma="strong:G5547">Lord</w> has <w lemma="strong:G1764">come</w>.</verse><verse osisID="2Thess.2.3" verseOrdinal="30838"><note n="g" osisID="2Thess.2.3!crossReference.g" osisRef="2Thess.2.3" type="crossReference"><reference osisRef="Eph.5.6">Eph. 5:6</reference></note><w lemma="strong:G1818">Let</w> <w lemma="strong:G3361">no</w> <w lemma="strong:G5100">one</w> <w lemma="strong:G1818">deceive</w> <w lemma="strong:G5209">you</w> <w lemma="strong:G2596">in</w> <w lemma="strong:G3367">any</w> <w lemma="strong:G5158">way</w>. <w lemma="strong:G3754">For</w> that day will not come, <note n="h" osisID="2Thess.2.3!crossReference.h" osisRef="2Thess.2.3" type="crossReference"><reference osisRef="1Tim.4.1">1 Tim. 4:1</reference></note><w lemma="strong:G3362">unless</w> the <w lemma="strong:G646">rebellion</w> <w lemma="strong:G2064">comes</w> <w lemma="strong:G4412">first</w>, <w lemma="strong:G2532">and</w> <note n="i" osisID="2Thess.2.3!crossReference.i" osisRef="2Thess.2.3" type="crossReference">[<reference osisRef="2Thess.2.8">ver. 8</reference>; <reference osisRef="Dan.7.25">Dan. 7:25</reference>; <reference osisRef="Dan.8.25">8:25</reference>; <reference osisRef="Dan.11.36">11:36</reference>; <reference osisRef="Rev.13.5-Rev.13.6">Rev. 13:5, 6</reference>]</note>the <w lemma="strong:G444">man</w> of <w lemma="strong:G266">lawlessness</w><note n="1" osisID="2Thess.2.3!note.1" osisRef="2Thess.2.3" type="variant">Some manuscripts <hi type="italic">sin</hi></note> is <w lemma="strong:G601">revealed</w>, <note n="j" osisID="2Thess.2.3!crossReference.j" osisRef="2Thess.2.3" type="crossReference"><reference osisRef="John.17.12">John 17:12</reference>; [<reference osisRef="Matt.23.15">Matt. 23:15</reference>]</note>the <w lemma="strong:G5207">son</w> of <w lemma="strong:G684">destruction</w>,<note n="2" osisID="2Thess.2.3!note.2" osisRef="2Thess.2.3" type="explanation">Greek <hi type="italic"><catchWord>the son of</catchWord> perdition</hi> (a Hebrew idiom)</note></verse><verse osisID="2Thess.2.4" verseOrdinal="30839"><w lemma="strong:G3588">who</w> <w lemma="strong:G480">opposes</w> <w lemma="strong:G2532">and</w> <w lemma="strong:G5229">exalts</w> himself <w lemma="strong:G1909">against</w> <w lemma="strong:G3956">every</w> <w lemma="strong:G3004">so-called</w> <w lemma="strong:G2316">god</w> <w lemma="strong:G2228">or</w> object of <w lemma="strong:G4574">worship</w>, <w lemma="strong:G5620">so</w> that <w lemma="strong:G846">he</w> <w lemma="strong:G5613 strong:G2316">takes</w> his <w lemma="strong:G2523">seat</w> <w lemma="strong:G1519">in</w> the <w lemma="strong:G3485">temple</w> of <w lemma="strong:G2316">God</w>, <note n="k" osisID="2Thess.2.4!crossReference.k" osisRef="2Thess.2.4" type="crossReference">[<reference osisRef="Isa.14.14">Isa. 14:14</reference>; <reference osisRef="Ezek.28.2">Ezek. 28:2</reference>]</note><w lemma="strong:G584">proclaiming</w> <w lemma="strong:G1438">himself</w> <w lemma="strong:G3754">to</w> <w lemma="strong:G2076">be</w> <w lemma="strong:G2316">God</w>.</verse><verse osisID="2Thess.2.5" verseOrdinal="30840">Do you <w lemma="strong:G3756">not</w> <w lemma="strong:G3421">remember</w> <w lemma="strong:G3754">that</w> when I <w lemma="strong:G5607">was</w> <w lemma="strong:G2089">still</w> <w lemma="strong:G4314">with</w> <w lemma="strong:G5209">you</w> I <w lemma="strong:G3004">told</w> <w lemma="strong:G5213">you</w> these <w lemma="strong:G5023">things</w>?</verse><verse osisID="2Thess.2.6" verseOrdinal="30841"><w lemma="strong:G2532">And</w> you <w lemma="strong:G1492">know</w> what is <w lemma="strong:G2722">restraining</w> him <w lemma="strong:G3568">now</w> so <w lemma="strong:G1519">that</w> <w lemma="strong:G846">he</w> may be <w lemma="strong:G601">revealed</w> <w lemma="strong:G1722">in</w> <w lemma="strong:G1438">his</w> <w lemma="strong:G2540">time</w>.</verse><verse osisID="2Thess.2.7" verseOrdinal="30842"><w lemma="strong:G1063">For</w> <note n="l" osisID="2Thess.2.7!crossReference.l" osisRef="2Thess.2.7" type="crossReference"><reference osisRef="Rev.17.5">Rev. 17:5</reference>, <reference osisRef="Rev.17.7">7</reference></note>the <w lemma="strong:G3466">mystery</w> of <w lemma="strong:G458">lawlessness</w> <note n="m" osisID="2Thess.2.7!crossReference.m" osisRef="2Thess.2.7" type="crossReference"><reference osisRef="1John.2.18">1 John 2:18</reference>; <reference osisRef="1John.4.3">4:3</reference></note><w lemma="strong:G1754">is</w> <w lemma="strong:G2235">already</w> at <w lemma="strong:G1754">work</w>. <w lemma="strong:G3440">Only</w> he who <w lemma="strong:G737">now</w> <w lemma="strong:G2722">restrains</w> it will do so <w lemma="strong:G2193">until</w> he <w lemma="strong:G1096">is</w> out <w lemma="strong:G1537">of</w> the <w lemma="strong:G3319">way</w>.</verse><verse osisID="2Thess.2.8" verseOrdinal="30843"><w lemma="strong:G2532">And</w> <w lemma="strong:G5119">then</w> <note n="n" osisID="2Thess.2.8!crossReference.n" osisRef="2Thess.2.8" type="crossReference">See <reference osisRef="2Thess.2.3">ver. 3</reference></note>the <w lemma="strong:G459">lawless one</w> will be <w lemma="strong:G601">revealed</w>, <w lemma="strong:G3739">whom</w> the <w lemma="strong:G2962">Lord</w> Jesus <note n="o" osisID="2Thess.2.8!crossReference.o" osisRef="2Thess.2.8" type="crossReference">[<reference osisRef="Dan.7.10-Dan.7.11">Dan. 7:10, 11</reference>]</note>will <w lemma="strong:G355">kill</w> with <note n="p" osisID="2Thess.2.8!crossReference.p" osisRef="2Thess.2.8" type="crossReference"><reference osisRef="Isa.11.4">Isa. 11:4</reference></note>the <w lemma="strong:G4151">breath</w> of <w lemma="strong:G846">his</w> <w lemma="strong:G4750">mouth</w> <w lemma="strong:G2532">and</w> bring to <w lemma="strong:G2673">nothing</w> by <note n="q" osisID="2Thess.2.8!crossReference.q" osisRef="2Thess.2.8" type="crossReference">[<reference osisRef="1Tim.6.14">1 Tim. 6:14</reference>; <reference osisRef="2Tim.1.10">2 Tim. 1:10</reference>; <reference osisRef="2Tim.4.1">4:1</reference>, <reference osisRef="2Tim.4.8">8</reference>; <reference osisRef="Titus.2.13">Titus 2:13</reference>]</note>the <w lemma="strong:G2015">appearance</w> of <w lemma="strong:G846">his</w> <w lemma="strong:G3952">coming</w>.</verse><verse osisID="2Thess.2.9" verseOrdinal="30844"><w lemma="strong:G3739">The</w> <w lemma="strong:G3952">coming</w> of the lawless one <w lemma="strong:G2076">is</w> <w lemma="strong:G2596">by</w> the <w lemma="strong:G1753">activity</w> of <w lemma="strong:G4567">Satan</w> <note n="r" osisID="2Thess.2.9!crossReference.r" osisRef="2Thess.2.9" type="crossReference">[<reference osisRef="Rev.13.14">Rev. 13:14</reference>]; See <reference osisRef="Matt.24.24">Matt. 24:24</reference></note><w lemma="strong:G1722">with</w> <w lemma="strong:G3956">all</w> <w lemma="strong:G1411">power</w> <w lemma="strong:G2532">and</w> <w lemma="strong:G5579">false</w> <w lemma="strong:G4592">signs</w> <w lemma="strong:G2532">and</w> <w lemma="strong:G5059">wonders</w>,</verse><verse osisID="2Thess.2.10" verseOrdinal="30845"><w lemma="strong:G2532">and</w> <w lemma="strong:G1722">with</w> <w lemma="strong:G3956">all</w> <w lemma="strong:G93">wicked</w> <w lemma="strong:G539">deception</w> <w lemma="strong:G1722">for</w> <note n="s" osisID="2Thess.2.10!crossReference.s" osisRef="2Thess.2.10" type="crossReference">See <reference osisRef="1Cor.1.18">1 Cor. 1:18</reference></note>those who are <w lemma="strong:G622">perishing</w>, <w lemma="strong:G473 strong:G3739">because</w> <w lemma="strong:G846">they</w> <w lemma="strong:G1209 strong:G3756">refused</w> to <w lemma="strong:G26">love</w> the <w lemma="strong:G225">truth</w> and <w lemma="strong:G1519">so</w> be <w lemma="strong:G4982">saved</w>.</verse><verse osisID="2Thess.2.11" verseOrdinal="30846"><w lemma="strong:G2532 strong:G5124 strong:G1223">Therefore</w> <note n="t" osisID="2Thess.2.11!crossReference.t" osisRef="2Thess.2.11" type="crossReference">[<reference osisRef="1Kgs.22.22">1 Kgs. 22:22</reference>; <reference osisRef="Ezek.14.9">Ezek. 14:9</reference>; <reference osisRef="Rev.17.17">Rev. 17:17</reference>]</note><w lemma="strong:G2316">God</w> <w lemma="strong:G3992">sends</w> <w lemma="strong:G846">them</w> a <w lemma="strong:G1753">strong</w> <w lemma="strong:G4106">delusion</w>, so <w lemma="strong:G1519">that</w> <w lemma="strong:G846">they</w> may <w lemma="strong:G4100">believe</w> <note n="u" osisID="2Thess.2.11!crossReference.u" osisRef="2Thess.2.11" type="crossReference">[<reference osisRef="1Thess.2.3">1 Thess. 2:3</reference>; <reference osisRef="1Tim.4.2">1 Tim. 4:2</reference>]; See <reference osisRef="Rom.1.25">Rom. 1:25</reference></note>what is <w lemma="strong:G5579">false</w>,</verse><verse osisID="2Thess.2.12" verseOrdinal="30847">in <w lemma="strong:G2443">order that</w> <w lemma="strong:G3956">all</w> may be <w lemma="strong:G2919">condemned</w> <note n="v" osisID="2Thess.2.12!crossReference.v" osisRef="2Thess.2.12" type="crossReference"><reference osisRef="Rom.2.8">Rom. 2:8</reference></note><w lemma="strong:G3588">who</w> did <w lemma="strong:G3361">not</w> <w lemma="strong:G4100">believe</w> the <w lemma="strong:G225">truth</w> <w lemma="strong:G235">but</w> <note n="w" osisID="2Thess.2.12!crossReference.w" osisRef="2Thess.2.12" type="crossReference">See <reference osisRef="Rom.1.32">Rom. 1:32</reference></note>had <w lemma="strong:G2106">pleasure</w> <w lemma="strong:G1722">in</w> <w lemma="strong:G93">unrighteousness</w>. <div eID="gen34216" type="paragraph" /> <div eID="gen34215" type="section" /></verse><div type="x-milestone" subType="x-preverse" sID="pv6468" /><div sID="gen34217" type="section" /> <title>Stand Firm</title> <div sID="gen34218" type="paragraph" /> <div type="x-milestone" subType="x-preverse" eID="pv6468" /><verse osisID="2Thess.2.13" verseOrdinal="30848"><w lemma="strong:G1161">But</w> <note n="x" osisID="2Thess.2.13!crossReference.x" osisRef="2Thess.2.13" type="crossReference"><reference osisRef="2Thess.1.3">ch. 1:3</reference></note><w lemma="strong:G2249">we</w> <w lemma="strong:G3784">ought</w> <w lemma="strong:G3842">always</w> to <w lemma="strong:G2168">give thanks</w> to <w lemma="strong:G2316">God</w> <w lemma="strong:G4012">for</w> <w lemma="strong:G5216">you</w>, <note n="y" osisID="2Thess.2.13!crossReference.y" osisRef="2Thess.2.13" type="crossReference"><reference osisRef="1Thess.1.4">1 Thess. 1:4</reference>; [<reference osisRef="Deut.33.12">Deut. 33:12</reference>]</note><w lemma="strong:G80">brothers</w> <w lemma="strong:G25">beloved</w> <w lemma="strong:G5259">by</w> the <w lemma="strong:G2962">Lord</w>, <w lemma="strong:G3754">because</w> <w lemma="strong:G2316">God</w> <w lemma="strong:G138">chose</w> <w lemma="strong:G5209">you</w> <note n="z" osisID="2Thess.2.13!crossReference.z" osisRef="2Thess.2.13" type="crossReference"><reference osisRef="Eph.1.4">Eph. 1:4</reference></note>as the <w lemma="strong:G575 strong:G746">firstfruits</w><note n="1" osisID="2Thess.2.13!note.1" osisRef="2Thess.2.13" type="variant">Some manuscripts <hi type="italic"><catchWord>chose you</catchWord> from the beginning</hi></note> <note n="a" osisID="2Thess.2.13!crossReference.a" osisRef="2Thess.2.13" type="crossReference"><reference osisRef="1Thess.5.9">1 Thess. 5:9</reference>; [<reference osisRef="2Tim.1.9">2 Tim. 1:9</reference>]</note><w lemma="strong:G1519">to</w> be <w lemma="strong:G4991">saved</w>, <note n="b" osisID="2Thess.2.13!crossReference.b" osisRef="2Thess.2.13" type="crossReference">See <reference osisRef="1Thess.4.3">1 Thess. 4:3</reference></note><w lemma="strong:G1722">through</w> <w lemma="strong:G38">sanctification</w> by the <w lemma="strong:G4151">Spirit</w> <w lemma="strong:G2532">and</w> <w lemma="strong:G4102">belief</w> in the <w lemma="strong:G225">truth</w>.</verse><verse osisID="2Thess.2.14" verseOrdinal="30849">To <w lemma="strong:G1519 strong:G3739">this</w> he <w lemma="strong:G2564">called</w> <w lemma="strong:G5209">you</w> <w lemma="strong:G1223">through</w> <note n="c" osisID="2Thess.2.14!crossReference.c" osisRef="2Thess.2.14" type="crossReference"><reference osisRef="1Thess.1.5">1 Thess. 1:5</reference></note><w lemma="strong:G2257">our</w> <w lemma="strong:G2098">gospel</w>, <note n="a" osisID="2Thess.2.14!crossReference.a" osisRef="2Thess.2.14" type="crossReference">[See <reference osisRef="2Thess.2.13">ver. 13</reference> above]</note>so <w lemma="strong:G1519">that</w> you may <w lemma="strong:G4047">obtain</w> the <w lemma="strong:G1391">glory</w> of <w lemma="strong:G2257">our</w> <w lemma="strong:G2962">Lord</w> <w lemma="strong:G2424">Jesus</w> <w lemma="strong:G5547">Christ</w>.</verse><verse osisID="2Thess.2.15" verseOrdinal="30850">So <w lemma="strong:G686 strong:G3767">then</w>, <w lemma="strong:G80">brothers</w>, <note n="d" osisID="2Thess.2.15!crossReference.d" osisRef="2Thess.2.15" type="crossReference">See <reference osisRef="1Cor.16.13">1 Cor. 16:13</reference></note><w lemma="strong:G4739">stand firm</w> <w lemma="strong:G2532">and</w> <w lemma="strong:G2902">hold to</w> <note n="e" osisID="2Thess.2.15!crossReference.e" osisRef="2Thess.2.15" type="crossReference"><reference osisRef="2Thess.3.6">ch. 3:6</reference>; <reference osisRef="1Cor.11.2">1 Cor. 11:2</reference></note>the <w lemma="strong:G3862">traditions</w> <w lemma="strong:G3739">that</w> you were <w lemma="strong:G1321">taught</w> by us, <w lemma="strong:G1535">either</w> <note n="f" osisID="2Thess.2.15!crossReference.f" osisRef="2Thess.2.15" type="crossReference"><reference osisRef="2Thess.2.2">ver. 2</reference></note><w lemma="strong:G1223">by</w> our spoken <w lemma="strong:G3056">word</w> <w lemma="strong:G1535 strong:G1223">or</w> by <note n="f" osisID="2Thess.2.15!crossReference.f" osisRef="2Thess.2.15" type="crossReference"><reference osisRef="2Thess.2.2">ver. 2</reference></note><w lemma="strong:G2257">our</w> <w lemma="strong:G1992">letter</w>. <div eID="gen34218" type="paragraph" /></verse><div type="x-milestone" subType="x-preverse" sID="pv6469" /><div sID="gen34219" type="paragraph" /> <div type="x-milestone" subType="x-preverse" eID="pv6469" /><verse osisID="2Thess.2.16" verseOrdinal="30851"><w lemma="strong:G1161">Now</w> may <w lemma="strong:G2257">our</w> <w lemma="strong:G2962">Lord</w> <w lemma="strong:G2424">Jesus</w> <w lemma="strong:G5547">Christ</w> <w lemma="strong:G846">himself</w>, <w lemma="strong:G2532">and</w> <w lemma="strong:G2316">God</w> <w lemma="strong:G2532 strong:G2257">our</w> <w lemma="strong:G3962">Father</w>, <note n="g" osisID="2Thess.2.16!crossReference.g" osisRef="2Thess.2.16" type="crossReference"><reference osisRef="John.3.16">John 3:16</reference>; <reference osisRef="1John.4.10">1 John 4:10</reference>; <reference osisRef="Rev.1.5">Rev. 1:5</reference></note><w lemma="strong:G3588">who</w> <w lemma="strong:G25">loved</w> <w lemma="strong:G2248">us</w> <w lemma="strong:G2532">and</w> <w lemma="strong:G1325">gave</w> us <w lemma="strong:G166">eternal</w> <w lemma="strong:G3874">comfort</w> <w lemma="strong:G2532">and</w> <w lemma="strong:G18">good</w> <note n="h" osisID="2Thess.2.16!crossReference.h" osisRef="2Thess.2.16" type="crossReference"><reference osisRef="1Pet.1.3">1 Pet. 1:3</reference></note><w lemma="strong:G1680">hope</w> <w lemma="strong:G1722">through</w> <w lemma="strong:G5485">grace</w>,</verse><verse osisID="2Thess.2.17" verseOrdinal="30852"><w lemma="strong:G3870">comfort</w> <w lemma="strong:G5216">your</w> <w lemma="strong:G2588">hearts</w> <w lemma="strong:G2532">and</w> <note n="i" osisID="2Thess.2.17!crossReference.i" osisRef="2Thess.2.17" type="crossReference"><reference osisRef="2Thess.3.3">ch. 3:3</reference>; <reference osisRef="1Thess.3.13">1 Thess. 3:13</reference></note><w lemma="strong:G4741">establish</w> <w lemma="strong:G5209">them</w> <w lemma="strong:G1722">in</w> <w lemma="strong:G3956">every</w> <w lemma="strong:G18">good</w> <w lemma="strong:G2041">work</w> <w lemma="strong:G2532">and</w> <w lemma="strong:G3056">word</w>. <div eID="gen34219" type="paragraph" /> <div eID="gen34217" type="section" /> <chapter eID="gen34214" osisID="2Thess.2" /></verse></div>
    `,
        `
        <div><title type="x-gen">04990</title><reference><hi type="bold">4990</hi></reference><lb /> מתרדת<lb /> [mithr<hi type="super"><seg type="font-size: -1;"><hi type="italic">e</hi></seg></hi>dâth] \\<hi type="italic">mith-red-awth'</hi>\\<lb /> Of Persian origin;<lb /><hi type="italic">{Mithredath}</hi> the name of two <hi type="bold">Persians:</hi> - Mithredath.</div>
        `,
        `
       <div><title type="x-gen">Psa 0</title><div osisID="Ps.0.0" type="x-gen"><div osisID="Ps" sID="gen7499" type="book" /><div sID="gen7500" type="introduction" /><div sID="gen7501" type="paragraph" />The book of Psalms is filled with the songs and prayers offered to God by the nation of Israel. Their expressions of praise, faith, sorrow, and frustration cover the range of human emotions. Some of the Psalms dwell on the treasure of wisdom and God’s Word. Others reveal the troubled heart of a mourner. Still others explode with praise to God and invite others to join in song. This diversity is unified by one element: they are centered upon the one and only living God. This Creator God is King of all the earth and a refuge to all who trust in him. Many of the Psalms are attributed to King David. The writing and collection of the Psalms into their present form spans the fifteenth to the third centuries <seg type="x-chronology">b.c.</seg><div eID="gen7501" type="paragraph" /><div eID="gen7500" type="introduction" /> <title canonical="true" type="x-psalm-book">Book One</title> </div><title type="x-gen">Psalms 1</title><div osisID="Ps.1.0" type="x-gen"><chapter osisID="Ps.1" sID="gen7502" /> </div><div type="x-milestone" subType="x-preverse" sID="pv2782" /><div sID="gen7503" type="section" /> <title>The Way of the Righteous and the Wicked</title> <lg sID="gen7504" /> <div type="x-milestone" subType="x-preverse" eID="pv2782" /><verse osisID="Ps.1.1" verseOrdinal="14440"> <l level="1" sID="gen7505" /><w lemma="strong:H0835">Blessed</w> is the <w lemma="strong:H0376">man</w><note n="1" osisID="Ps.1.1!note.1" osisRef="Ps.1.1" type="explanation">The singular Hebrew word for <catchWord><hi type="italic">man</hi></catchWord> (<hi type="italic">ish</hi>) is used here to portray a representative example of a godly person; see Preface</note><l eID="gen7505" level="1" /> <l level="2" sID="gen7506" />who <note n="a" osisID="Ps.1.1!crossReference.a" osisRef="Ps.1.1" type="crossReference"><reference osisRef="Prov.4.14-Prov.4.15">Prov. 4:14, 15</reference></note><w lemma="strong:H01980">walks</w> not in <note n="b" osisID="Ps.1.1!crossReference.b" osisRef="Ps.1.1" type="crossReference"><reference osisRef="Job.21.16">Job 21:16</reference></note>the <w lemma="strong:H06098">counsel</w> of the <w lemma="strong:H07563">wicked</w>,<l eID="gen7506" level="2" /> <l level="1" sID="gen7507" />nor <w lemma="strong:H05975">stands</w> in <note n="c" osisID="Ps.1.1!crossReference.c" osisRef="Ps.1.1" type="crossReference"><reference osisRef="Prov.1.10">Prov. 1:10</reference></note>the <w lemma="strong:H01870">way</w> of <w lemma="strong:H02400">sinners</w>,<l eID="gen7507" level="1" /> <l level="2" sID="gen7508" />nor <note n="d" osisID="Ps.1.1!crossReference.d" osisRef="Ps.1.1" type="crossReference"><reference osisRef="Ps.26.4">Ps. 26:4</reference>; <reference osisRef="Jer.15.17">Jer. 15:17</reference></note><w lemma="strong:H03427">sits</w> in <note n="e" osisID="Ps.1.1!crossReference.e" osisRef="Ps.1.1" type="crossReference">[<reference osisRef="Ps.107.32">Ps. 107:32</reference>]</note>the <w lemma="strong:H04186">seat</w> of <note n="f" osisID="Ps.1.1!crossReference.f" osisRef="Ps.1.1" type="crossReference"><reference osisRef="Prov.1.22">Prov. 1:22</reference>; <reference osisRef="Prov.3.34">3:34</reference>; <reference osisRef="Prov.19.29">19:29</reference>; <reference osisRef="Prov.21.24">21:24</reference>; <reference osisRef="Prov.29.8">29:8</reference>; [<reference osisRef="Isa.28.14">Isa. 28:14</reference>]</note><w lemma="strong:H03887">scoffers</w>;<l eID="gen7508" level="2" /> </verse><verse osisID="Ps.1.2" verseOrdinal="14441"><l level="1" sID="gen7509" />but his <note n="g" osisID="Ps.1.2!crossReference.g" osisRef="Ps.1.2" type="crossReference"><reference osisRef="Ps.112.1">Ps. 112:1</reference>; <reference osisRef="Ps.119.35">119:35</reference>, <reference osisRef="Ps.119.47">47</reference>, <reference osisRef="Ps.119.92">92</reference></note><w lemma="strong:H02656">delight</w> is in the <w lemma="strong:H08451">law</w><note n="1" osisID="Ps.1.2!note.1" osisRef="Ps.1.2" type="alternative">Or <hi type="italic">instruction</hi></note> of the <divineName><w lemma="strong:H03068">Lord</w></divineName>,<l eID="gen7509" level="1" /> <l level="2" sID="gen7510" />and on his <note n="h" osisID="Ps.1.2!crossReference.h" osisRef="Ps.1.2" type="crossReference"><reference osisRef="Ps.119.1">Ps. 119:1</reference>, <reference osisRef="Ps.119.97">97</reference>; <reference osisRef="Josh.1.8">Josh. 1:8</reference></note><w lemma="strong:H08451">law</w> he <w lemma="strong:H01897">meditates</w> <w lemma="strong:H03119">day</w> and <w lemma="strong:H03915">night</w>.<l eID="gen7510" level="2" />  <lg eID="gen7504" /></verse><div type="x-milestone" subType="x-preverse" sID="pv2783" /><lg sID="gen7511" /> <div type="x-milestone" subType="x-preverse" eID="pv2783" /><verse osisID="Ps.1.3" verseOrdinal="14442"> <l level="1" sID="gen7512" />He is like <note n="i" osisID="Ps.1.3!crossReference.i" osisRef="Ps.1.3" type="crossReference"><reference osisRef="Jer.17.8">Jer. 17:8</reference>; <reference osisRef="Ezek.19.10">Ezek. 19:10</reference>; [<reference osisRef="Num.24.6">Num. 24:6</reference>; <reference osisRef="Job.29.19">Job 29:19</reference>]</note>a <w lemma="strong:H06086">tree</w><l eID="gen7512" level="1" /> <l level="2" sID="gen7513" /><w lemma="strong:H08362">planted</w> by <note n="j" osisID="Ps.1.3!crossReference.j" osisRef="Ps.1.3" type="crossReference"><reference osisRef="Ps.46.4">Ps. 46:4</reference></note><w lemma="strong:H06388">streams</w> of <w lemma="strong:H04325">water</w><l eID="gen7513" level="2" /> <l level="1" sID="gen7514" />that <w lemma="strong:H05414">yields</w> its <w lemma="strong:H06529">fruit</w> in its <w lemma="strong:H06256">season</w>,<l eID="gen7514" level="1" /> <l level="2" sID="gen7515" />and its <note n="k" osisID="Ps.1.3!crossReference.k" osisRef="Ps.1.3" type="crossReference"><reference osisRef="Ezek.47.12">Ezek. 47:12</reference>; [<reference osisRef="Isa.34.4">Isa. 34:4</reference>]</note><w lemma="strong:H05929">leaf</w> does not <w lemma="strong:H05034">wither</w>.<l eID="gen7515" level="2" /> <l level="1" sID="gen7516" /><note n="l" osisID="Ps.1.3!crossReference.l" osisRef="Ps.1.3" type="crossReference"><reference osisRef="Gen.39.3">Gen. 39:3</reference>, <reference osisRef="Gen.39.23">23</reference>; [<reference osisRef="Ps.128.2">Ps. 128:2</reference>; <reference osisRef="Isa.3.10">Isa. 3:10</reference>]</note>In all that he <w lemma="strong:H06213">does</w>, he <w lemma="strong:H06743">prospers</w>.<l eID="gen7516" level="1" /> </verse><verse osisID="Ps.1.4" verseOrdinal="14443"><l level="1" sID="gen7517" />The <w lemma="strong:H07563">wicked</w> are not <w lemma="strong:H03651">so</w>,<l eID="gen7517" level="1" /> <l level="2" sID="gen7518" />but are like <note n="m" osisID="Ps.1.4!crossReference.m" osisRef="Ps.1.4" type="crossReference">See <reference osisRef="Job.21.18">Job 21:18</reference></note><w lemma="strong:H04671">chaff</w> that the <w lemma="strong:H07307">wind</w> <w lemma="strong:H05086">drives</w> away.<l eID="gen7518" level="2" />  <lg eID="gen7511" /></verse><div type="x-milestone" subType="x-preverse" sID="pv2784" /><lg sID="gen7519" /> <div type="x-milestone" subType="x-preverse" eID="pv2784" /><verse osisID="Ps.1.5" verseOrdinal="14444"> <l level="1" sID="gen7520" /><w lemma="strong:H05921 strong:H03651">Therefore</w> the <w lemma="strong:H07563">wicked</w> <note n="n" osisID="Ps.1.5!crossReference.n" osisRef="Ps.1.5" type="crossReference"><reference osisRef="Ps.5.5">Ps. 5:5</reference>; <reference osisRef="Ps.76.7">76:7</reference>; <reference osisRef="Nah.1.6">Nah. 1:6</reference>; <reference osisRef="Luke.21.36">Luke 21:36</reference>; <reference osisRef="Eph.6.13">Eph. 6:13</reference></note>will not <w lemma="strong:H06965">stand</w> in the <w lemma="strong:H04941">judgment</w>,<l eID="gen7520" level="1" /> <l level="2" sID="gen7521" />nor <w lemma="strong:H02400">sinners</w> in <note n="o" osisID="Ps.1.5!crossReference.o" osisRef="Ps.1.5" type="crossReference">[<reference osisRef="Ezek.13.9">Ezek. 13:9</reference>]</note>the <w lemma="strong:H05712">congregation</w> of the <w lemma="strong:H06662">righteous</w>;<l eID="gen7521" level="2" /> </verse><verse osisID="Ps.1.6" verseOrdinal="14445"><l level="1" sID="gen7522" />for the <divineName><w lemma="strong:H03068">Lord</w></divineName> <note n="p" osisID="Ps.1.6!crossReference.p" osisRef="Ps.1.6" type="crossReference"><reference osisRef="Ps.31.7">Ps. 31:7</reference>; <reference osisRef="Ps.37.18">37:18</reference>; <reference osisRef="Ps.144.3">144:3</reference>; <reference osisRef="Nah.1.7">Nah. 1:7</reference>; [<reference osisRef="John.10.14">John 10:14</reference>; <reference osisRef="2Tim.2.19">2 Tim. 2:19</reference>]</note><w lemma="strong:H03045">knows</w> <note n="q" osisID="Ps.1.6!crossReference.q" osisRef="Ps.1.6" type="crossReference"><reference osisRef="Ps.37.5">Ps. 37:5</reference></note>the <w lemma="strong:H01870">way</w> of the <w lemma="strong:H06662">righteous</w>,<l eID="gen7522" level="1" /> <l level="2" sID="gen7523" />but the <w lemma="strong:H01870">way</w> of the <w lemma="strong:H07563">wicked</w> will <w lemma="strong:H06">perish</w>.<l eID="gen7523" level="2" />  <lg eID="gen7519" /> <div eID="gen7503" type="section" /> <chapter eID="gen7502" osisID="Ps.1" /></verse></div> 
        `
    ]
    let count = 0;
    //testData.reverse();
    //testData.splice(0, testData.length -1);
    //testData = [testData[testData.length -2]];

    testBookmarks = [
        {
            id: 10,
            ordinalRange: [30850, 30850],
            offsetRange: null,
            labels: [1],
            bookInitials: "KJV",
            notes: null,
            text: "smaller text",
            fullText: 'Fuller <span class="highlight">text</span>',
            type: "bookmark",
            verseRange: "Phil 1:2",
        },
        {
            id: 9,
            ordinalRange: [30852, 30852],
            offsetRange: [50, 60],
            labels: [1],
            bookInitials: "KJV",
            notes: null,
            text: "test",
            type: "bookmark",
        },
        {
            id: 8,
            ordinalRange: [30852, 30852],
            offsetRange: [40, 60],
            labels: [1],
            bookInitials: "KJV",
            notes: null,
            text: "test",
            type: "bookmark",
        },
        {
            id: 7,
            ordinalRange: [30852, 30852],
            offsetRange: [30, 60],
            labels: [1],
            bookInitials: "KJV",
            notes: null,
            text: "test",
            type: "bookmark",
        },
        {
            id: 6,
            ordinalRange: [30852, 30852],
            offsetRange: [20, 60],
            labels: [1],
            bookInitials: "KJV",
            text: "test",
            notes: "test",
            type: "bookmark",
        },
        {
            id: 5,
            ordinalRange: [30852, 30853],
            offsetRange: [10, 50],
            labels: [1],
            bookInitials: "KJV",
            notes: null,
            text: "test",
            type: "bookmark",
        },
        {
            id: 4,
            ordinalRange: [30835, 30836],
            offsetRange: [10, 50],
            labels: [1],
            bookInitials: "KJV",
            notes: null,
            text: "test",
            type: "bookmark",
        },
        {
            id: 0,
            ordinalRange: [30839, 30842],
            offsetRange: null,
            labels: [2],
            bookInitials: "KJV",
            notes: null,
            type: "bookmark",
        },
        {
            id: 1,
            ordinalRange: [30843, 30843],
            labels: [3],
            offsetRange: null,
            bookInitials: null,
            notes: null,
            text: "test",
            type: "bookmark",
        },
        {
            id: 2,
            ordinalRange: [30842, 30846],
            labels: [3],
            offsetRange: null,
            bookInitials: null,
            notes: null,
            text: "test",
            type: "bookmark",
        },
        {
            id: 3,
            ordinalRange: [30838, 30838],
            offsetRange: [10, 50],
            labels: [1, 2, 3, -5, -4, -3, -2, -1],
            bookInitials: "KJV",
            notes: "Testinote pitkä pitkä pitkä asdlfk lasdkfj laskdf laksdf laksdj laskdjf laksjd laksjdf laskdj" +
                "asldfkj alskdf alskdj laskdj alskdj alskdj asldkj ladfjlsjflajfasfkjad jfl kjdfljsdflja flkjasfl" +
                "asldfkj alskdf alskdj laskdj alskdj alskdj asldkj ladfjlsjflajfasfkjad jfl kjdfljsdflja flkjasfl" +
                "asldfkj alskdf alskdj laskdj alskdj alskdj asldkj ladfjlsjflajfasfkjad jfl kjdfljsdflja flkjasfl" +
                "asldfkj alskdf alskdj laskdj alskdj alskdj asldkj ladfjlsjflajfasfkjad jfl kjdfljsdflja flkjasfl" +
                "asldfkj alskdf alskdj laskdj alskdj alskdj asldkj ladfjlsjflajfasfkjad jfl kjdfljsdflja flkjasfl" +
                "asldfkj alskdf alskdj laskdj alskdj alskdj asldkj ladfjlsjflajfasfkjad jfl kjdfljsdflja flkjasfl" +
                "asldfkj alskdf alskdj laskdj alskdj alskdj asldkj ladfjlsjflajfasfkjad jfl kjdfljsdflja flkjasfl" +
                "asldfkj alskdf alskdj laskdj alskdj alskdj asldkj ladfjlsjflajfasfkjad jfl kjdfljsdflja flkjasfl" +
                "asldfkj alskdf alskdj laskdj alskdj alskdj asldkj ladfjlsjflajfasfkjad jfl kjdfljsdflja flkjasfl",
            text: "asdf asdf asdf asdf asdf asdf",
            type: "bookmark",
        },
    ];
    testBookmarkLabels = [
        {
            id: 1,
            name: "first",
            style: {
                color: new Color("cyan").rgb().array() //[255, 0, 0]
            }
        },
        {
            id: 2,
            name: "second",
            style: {
                color: new Color("yellow").rgb().array(), //[0, 255, 0],
            }
        },
        {
            id: 3,
            name: "third",
            style: {
                color: new Color("magenta").rgb().array(), //[0, 0, 255],
            }
        },
        {
            id: -5,
            name: "minusfive",
            style: {color: [255,0,0,255]}
        },
        {
            id: -1,
            name: "minusone",
            style: {color: [255,0,0,255]}
        },
        {
            id: -2,
            name: "minustwo",
            style: {color: [255,255,0,255]}
        },
        {
            id: -3,
            name: "minusthree",
            style: {color: [255,0,255,255]}
        },
        {
            id: -4,
            name: "minusfour",
            style: {color: [0,255,255,255]}
        }
    ];
    const journalTextEntries = [
            {
                id: 1,
                text: "<div>Journal text 1<ul><li>test1</li><li>test2</li></ul></div>",
                labelId: 1,
                orderNumber: 1,
                indentLevel: 0,
                type: "journal",
            },
            {
                id: 2,
                text: "Journal text 2",
                labelId: 1,
                orderNumber: 2,
                indentLevel: 1,
                type: "journal",
            },
        {
            id: 3,
            text: "",
            labelId: 1,
            orderNumber: 2,
            indentLevel: 1,
            type: "journal",
        },
        ];
    testBookmarks.forEach(b => {
        b.verseRangeAbbreviated = "Phil 1:2";
        b.verseRangeOnlyNumber = "2";
        b.verseRange = "Philipians 1:2";
        b.bibleUrl = "http://asdf";
        b.fullText = "FULL TEXT!";
        b.osisFragment = {
            xml: "<div>test</div>"
        }
        //b.bookmarkToLabel = {orderNumber: 1, indentLevel: 1};
        //b.notes = "Bookmark notes asdf asdf asdf asdf";
    })

    const bookmarkToLabels = testBookmarks.map(b => (
        {
            bookmarkId: b.id,
            labelId: b.labels[0],
            orderNumber: 1,
            indentLevel: 1,
        }))

    const mode = "journal"

    if(mode === "bible") {
        testData = testData.map(
            v => ({
                type: "bible",
                osisFragment:
                    {
                        xml: v,
                        key: `KJV--${count++}`,
                        features: {},
                        bookCategory: "BIBLE",
                        bookInitials: "KJV",
                    }
                ,
                bookInitials: "KJV",
                bookmarks: testBookmarks,
                ordinalRange: [0, 1000],
            }));
        testData[2].ordinalRange = [30835, 30852];
    } else if(mode === "journal") {
        testData = [{
            type: "journal",
            label: testBookmarkLabels[0],
            bookmarks: testBookmarks,
            bookmarkToLabels,
            journalTextEntries,
        }];
    } else if(mode === "notes") {
        testData = [{
            type: "notes",
            label: testBookmarkLabels[0],
            bookmarks: testBookmarks,
            journalTextEntries,
            verseRange: "Philipians 1"
        }];
    } else if(mode === "multi") {
        testData = testData.map(
            v => ({
                type: "multi",
                osisFragments: [
                    {
                        xml: v,
                        keyName: "Key name",
                        osisRef: "Gen.1.1",
                        features: {type: "strongs", keyName: "ASDF"},
                        bookCategory: "BIBLE",
                    }
                ],
                bookInitials: "KJV",
                bookmarks: testBookmarks,
            }));
    }
    window.testData = testData;
}
