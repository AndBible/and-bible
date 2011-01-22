<?xml version="1.0"?> 
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- Whether to start each verse on an new line or not -->
  <xsl:param name="VLine" select="'false'"/>

  <!-- Whether to output no Verse numbers -->
  <xsl:param name="NoVNum" select="'false'"/>

  <!-- Whether to show non-canonical "headings" or not -->
  <xsl:param name="Headings" select="'true'"/>

  <!-- The order of display. Hebrew is rtl (right to left) -->
  <xsl:param name="direction" select="'ltr'"/>

<xsl:template match="/">
<html dir="{$direction}"><head><meta charset="utf-8"/></head><body>
    	<xsl:apply-templates/>
</body></html>
</xsl:template>

  <!--=======================================================================-->
  <!--
    == A proper OSIS document has osis as it's root.
    == We dig deeper for it's content.
    -->
  <xsl:template match="osis">
    <xsl:apply-templates/>
  </xsl:template>

  <!--=======================================================================-->
  <!--
    == An OSIS document may contain more that one work.
    == Each work is held in an osisCorpus element.
    == If there is only one work, then this element will (should) be absent.
    == Process each document in turn.
    == It might be reasonable to dig into the header element of each work
    == and get its title.
    == Otherwise, we ignore the header and work elements and just process
    == the osisText elements.
    -->
  <xsl:template match="osisCorpus">
    <xsl:apply-templates select="osisText"/>
  </xsl:template>

  <!--=======================================================================-->
  <!--
    == Each work has an osisText element.
    == We ignore the header and work elements and process its div elements.
    == While divs can be milestoned, the osisText element requires container
    == divs.
    -->
  <xsl:template match="osisText">
    <xsl:apply-templates select="div"/>
  </xsl:template>


<xsl:template match="title">
  <h3>
  <xsl:apply-templates/>
  </h3>
</xsl:template>

<xsl:template match="section">
    <xsl:apply-templates/>
</xsl:template>

<xsl:template match="note">
</xsl:template>

<xsl:template match="verse">
    <xsl:apply-templates/>
</xsl:template>

  <!--=======================================================================-->
  <!-- Handle verses as containers and as a start verse.                     -->
  <xsl:template match="verse[not(@eID)]">
    <!-- output each preverse element in turn -->
    <xsl:for-each select=".//*[@subType = 'x-preverse' or @subtype = 'x-preverse']">
      <xsl:choose>
        <xsl:when test="local-name() = 'title'">
          <!-- Always show canonical titles or if headings is turned on -->
          <xsl:if test="@canonical = 'true' or $Headings = 'true'">
            <h3 class="heading"><xsl:apply-templates /></h3>
          </xsl:if>
        </xsl:when>
        <xsl:otherwise>
          <xsl:apply-templates />
        </xsl:otherwise>
      </xsl:choose>
    </xsl:for-each>
    
    <!-- Handle the KJV paragraph marker. -->
    <xsl:if test="milestone[@type = 'x-p']"><br /><br /></xsl:if>
    <!-- If the verse doesn't start on its own line and -->
    <!-- the verse is not the first verse of a set of siblings, -->
    <!-- output an extra space. -->
    <!--  this was &#160; but it displayed as a weird character -->
    <xsl:if test="$VLine = 'false' and preceding-sibling::*[local-name() = 'verse']">
      <xsl:text> </xsl:text>
    </xsl:if>
    <!-- Always output the verse -->
    <xsl:call-template name="versenum"/><xsl:apply-templates/>
    <!-- Follow the verse with an extra space -->
    <!-- when they don't start on lines to themselves -->
    <xsl:text> </xsl:text>
  </xsl:template>

  <xsl:template name="versenum">
    <!-- Are verse numbers wanted? -->
    <xsl:if test="$NoVNum = 'false'">
      <!-- An osisID can be a space separated list of them -->
      <xsl:variable name="firstOsisID" select="substring-before(concat(@osisID, ' '), ' ')"/>
      <!-- If n is present use it for the number -->
      <xsl:variable name="verse">
          <xsl:value-of select="substring-after(substring-after($firstOsisID, '.'), '.')"/>
      </xsl:variable>
      <xsl:variable name="versenum">
         <xsl:value-of select="$verse"/>
      </xsl:variable>
      <sup class="verse"><xsl:value-of select="$versenum"/></sup>
    </xsl:if>
  </xsl:template>
  
  <!--=======================================================================-->
  <xsl:template match="title[@subType ='x-preverse' or @subtype = 'x-preverse']">
  <!-- Done by a line in [verse]
    <h3 class="heading">
      <xsl:apply-templates/>
    </h3>
  -->
  </xsl:template>

  <xsl:template match="title[@subType ='x-preverse' or @subtype = 'x-preverse']" mode="jesus">
  <!-- Done by a line in [verse]
    <h3 class="heading">
      <xsl:apply-templates/>
    </h3>
  -->
  </xsl:template>
  

<xsl:template match="ut">
  <font size="-2" color="#ff0000">
  <xsl:text>[</xsl:text>
  <xsl:apply-templates/>
  <xsl:text>]</xsl:text>
  </font>
</xsl:template>

<!-- type="x-begin-paragraph" -->
<xsl:template match="lb">
  <xsl:text><p /></xsl:text>
  <xsl:apply-templates/>
</xsl:template>

<xsl:template match="p">
  <xsl:text><p /></xsl:text>
  <xsl:apply-templates/>
</xsl:template>

<!-- not done
  ue_psnote
  ue_small
  ue_christ
  ue_poetry
  ue_clarify
  ue_quote
  ut_head
-->
  
<!-- Breaks HTMLDocument
  <meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1"/>
  <meta http-equiv="Expires" content="0"/>
-->

  <!-- Ignore headers and its elements -->
  <xsl:template match="header"/>
  <xsl:template match="revisionDesc"/>
  <xsl:template match="work"/>
   <!-- <xsl:template match="title"/> who's parent is work -->
  <xsl:template match="contributor"/>
  <xsl:template match="creator"/>
  <xsl:template match="subject"/>
  <!-- <xsl:template match="date"/> who's parent is work -->
  <xsl:template match="description"/>
  <xsl:template match="publisher"/>
  <xsl:template match="type"/>
  <xsl:template match="format"/>
  <xsl:template match="identifier"/>
  <xsl:template match="source"/>
  <xsl:template match="language"/>
  <xsl:template match="relation"/>
  <xsl:template match="coverage"/>
  <xsl:template match="rights"/>
  <xsl:template match="scope"/>
  <xsl:template match="workPrefix"/>
  <xsl:template match="castList"/>
  <xsl:template match="castGroup"/>
  <xsl:template match="castItem"/>
  <xsl:template match="actor"/>
  <xsl:template match="role"/>
  <xsl:template match="roleDesc"/>
  <xsl:template match="teiHeader"/>
  <xsl:template match="refSystem"/>


  <!-- Ignore titlePage -->
  <xsl:template match="titlePage"/>



</xsl:stylesheet>
