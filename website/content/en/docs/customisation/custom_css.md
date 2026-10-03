# Custom CSS

## Custom CSS for individual modules or globally (AndBible 4.0+) {#custom-css-for-individual-modules-or-globally-andbible-4-0}

*AndBible* supports the inclusion of custom CSS and fonts in module zip
files. Place your CSS file under the module directory (or subdirectory), and
define the CSS file path in the config file:
`AndBibleCSS=path-to/yourcssfile.css`

The CSS file must be formatted as follows:

- Prefix everything with the `.sword-YOURMODULE` class (where `YOURMODULE`
    is the module initials of your module).
- For night mode, prefix everything with the `.night` class.

If you have used `<hi type="my-type">` tag in your module, you can
define CSS for your tags with `.hi-my-type` class definitions.

## [Example](https://github.com/AndBible/special-modules/tree/master/HISB) {#id1}

**mods.d/HISB.conf**

```
[HISB]
DataPath=./modules/texts/ztext/HISB/
AndBibleCSS=style/style.css
...
```

Put your font file SILEOTSR.ttf to ./modules/texts/ztext/HISB/style/:

**modules/texts/ztext/HISB/style/style.css**

```
@font-face {
    font-family: 'Custom HISB Font';
    src: url('./SILEOTSR.ttf') format('truetype');
}

.sword-HISB {
    font-family: 'Custom HISB Font';
}

.sword-HISB .hi-x-trlit {
    color: green;
    font-size: 0.7em;
    vertical-align: top;
    font-weight: bold;
}

.night .sword-HISB {
    vertical-align: text-top;

    color: #00FF00; /*#green; */
}

.night .sword-HISB .hi-x-trlit {
    color:  white; /*#green;*/
    font-size: 0.7em;
    vertical-align: top;
    font-weight: bold;
}
```

## Global Styling

If you want to create a module that alters styles globally (i.e. how *AndBible*
looks overall), you can create an add-on module (i.e. put
`Category=And Bible`) and add the `AndBibleProvidesStyle=True` configuration
line to the conf.

Example config:

```
[StyleExample]
DataPath=./modules/texts/ztext/StyleExample/
Version=1.0
ShortPromo=Module that provides custom CSS and applies to all documents. Also provides example font.
Description=Module that provides custom CSS and applies to all documents.
DistributionLicense=Copyrighted; Freely distributable
Category=And Bible
ModDrv=RawGenBook
InstallSize=194837
AndBibleMinimumVersion=539
AndBibleProvidesFont=Style Example Font;style/SILEOTSR.ttf
AndBibleProvidesStyle=True
AndBibleCSS=style/style.css
```

## Debugging CSS

Please see [Android Webview Remote debugging
guide](https://developer.chrome.com/docs/devtools/remote-debugging/)
for how you can manipulate css.

## Examples

See
<https://github.com/AndBible/special-modules/>

## Want a ready-made custom CSS module?

Tuomas can make it. For most customisation, it takes usually 30 minutes of work
(see [pricing](https://shop.andbible.org/page/info)). You can ask for a
quote via [email](mailto:help.andbible%40gmail.com).
