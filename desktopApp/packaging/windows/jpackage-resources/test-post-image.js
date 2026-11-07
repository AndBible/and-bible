// Run with node; exercises the actual JScript against a small MSXML-compatible fixture.
const fs = require('fs');
const vm = require('vm');
const assert = require('assert');
const script = fs.readFileSync(__dirname + '/AndBible-post-image.wsf', 'utf8').match(/<!\[CDATA\[([\s\S]*?)\]\]>/)[1];
function run(names) {
    const shortcuts = names.map(name => ({name, children: [], appendChild(node) { this.children.push(node); }}));
    const xml = {
        async: true, setProperty() {}, load(path) { assert(path.endsWith('bundle.wxf')); return true; },
        selectNodes(query) { assert(query.includes("@Name='AndBible'")); const found = shortcuts.filter(s => s.name === 'AndBible'); return {length: found.length, item: i => found[i]}; },
        createNode(type, name, namespace) { return {type, name, namespace, attributes: {}, setAttribute(k, v) { this.attributes[k] = v; }}; },
        save() { this.saved = true; },
    };
    const context = { WScript: {ScriptFullName: 'config/AndBible-post-image.wsf'}, ActiveXObject: function(name) {
        if (name === 'Msxml2.DOMDocument.6.0') return xml;
        return {GetParentFolderName: () => 'config', BuildPath: (a, b) => a + '/' + b};
    }};
    vm.runInNewContext(script, context);
    return {shortcuts, xml};
}
const result = run(['AndBible', 'Other', 'AndBible']);
assert(result.xml.saved);
for (const index of [0, 2]) {
    const property = result.shortcuts[index].children[0];
    assert.strictEqual(property.name, 'ShortcutProperty');
    assert.strictEqual(property.namespace, 'http://schemas.microsoft.com/wix/2006/wi');
    assert.strictEqual(property.attributes.Key, 'System.AppUserModel.ID');
    assert.strictEqual(property.attributes.Value, 'org.andbible.AndBible');
}
assert.strictEqual(result.shortcuts[1].children.length, 0);
assert.throws(() => run(['Other']), /shortcut not found/);
console.log('post-image fixture: 2 shortcuts patched, unrelated shortcut untouched, missing shortcut rejected');
