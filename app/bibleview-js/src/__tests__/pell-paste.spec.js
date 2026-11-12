import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {init} from '../lib/pell/pell';

function transferEvent(type, text, {files = [], types = ['text/plain', 'text/html']} = {}) {
    const event = new Event(type, {bubbles: true, cancelable: true});
    Object.defineProperty(event, type === 'paste' ? 'clipboardData' : 'dataTransfer', {value: {
        types, files,
        getData: format => format === 'text/plain' ? text : '<span style="font-size:40px">Foreign HTML</span>',
    }});
    return event;
}

function select(node, start = 0, end = start) {
    const range = document.createRange();
    range.setStart(node, start);
    range.setEnd(node, end);
    window.getSelection().removeAllRanges();
    window.getSelection().addRange(range);
}

describe('note editor plain-text transfers', () => {
    let content;
    let onChange;

    beforeEach(() => {
        // jsdom has no editing commands: exercise the real Selection/Range fallback.
        document.execCommand = vi.fn(() => false);
        const element = document.createElement('div');
        document.body.appendChild(element);
        onChange = vi.fn();
        init({element, actions: [], onChange});
        content = element.content;
        select(content);
        document.execCommand.mockClear();
    });

    afterEach(() => {
        window.getSelection().removeAllRanges();
        document.body.innerHTML = '';
        delete document.execCommand;
        vi.restoreAllMocks();
    });

    it('uses Enter commands for every newline convention instead of raw newlines', () => {
        document.execCommand.mockReturnValue(true);
        content.dispatchEvent(transferEvent('paste', 'First\r\nSecond\r\n\rThird\n'));
        expect(document.execCommand.mock.calls).toEqual([
            ['insertText', false, 'First'],
            ['insertParagraph', false, null],
            ['insertText', false, 'Second'],
            ['insertParagraph', false, null],
            ['insertParagraph', false, null],
            ['insertText', false, 'Third'],
            ['insertParagraph', false, null],
        ]);
    });

    it('falls back to safe text nodes and explicit breaks and reports the changed HTML', () => {
        const event = transferEvent('paste', 'First <tag>\r\nSecond\r\rFourth');
        content.dispatchEvent(event);
        expect(event.defaultPrevented).toBe(true);
        expect(content.innerHTML).toBe('First &lt;tag&gt;<br>Second<br><br>Fourth');
        expect(onChange).toHaveBeenLastCalledWith(content.innerHTML);
    });

    it('replaces selected text without losing the suffix or interpreting HTML', () => {
        content.innerHTML = '<div>Before <b>selected</b> after</div>';
        select(content.querySelector('b').firstChild, 0, 8);
        content.dispatchEvent(transferEvent('paste', '<img src=x onerror=alert(1)>\nnext'));
        expect(content.innerHTML).toBe('<div>Before <b>&lt;img src=x onerror=alert(1)&gt;<br>next</b> after</div>');
        expect(content.querySelector('img')).toBeNull();
        content.dispatchEvent(transferEvent('paste', '!'));
        expect(content.textContent).toBe('Before <img src=x onerror=alert(1)>next! after');
    });

    it('keeps leading, consecutive and trailing line breaks in the fallback DOM', () => {
        content.dispatchEvent(transferEvent('paste', '\nFirst\n\n'));
        expect(content.innerHTML).toBe('<br>First<br><br><br>');
        // The last break is a caret placeholder, so typing continues on the last empty line.
        content.dispatchEvent(transferEvent('paste', 'Last'));
        expect(content.innerHTML).toBe('<br>First<br><br>Last<br>');
    });

    it('shows the final empty line when pasting at the end of an existing text node', () => {
        content.innerHTML = '<div>abc</div>';
        select(content.firstChild.firstChild, 3);
        content.dispatchEvent(transferEvent('paste', 'X\n'));
        expect(content.innerHTML).toBe('<div>abcX<br><br></div>');
    });

    it('does not change a selection outside the editor', () => {
        const outside = document.createElement('p');
        outside.textContent = 'Outside';
        document.body.appendChild(outside);
        select(outside.firstChild, 0, 7);
        content.innerHTML = 'Note';
        content.dispatchEvent(transferEvent('paste', '!'));
        expect(outside.textContent).toBe('Outside');
        expect(content.innerHTML).toBe('Note!');
    });

    it('appends safely when the browser has lost its selection', () => {
        content.innerHTML = '<div>Note</div>';
        window.getSelection().removeAllRanges();
        content.dispatchEvent(transferEvent('paste', ' More'));
        expect(content.innerHTML).toBe('<div>Note</div> More');
        expect(onChange).toHaveBeenLastCalledWith(content.innerHTML);
    });

    it('continues blocking real image file pastes even when text is present', () => {
        const event = transferEvent('paste', 'Image caption', {
            types: ['Files', 'text/plain'], files: [new File(['image'], 'image.png', {type: 'image/png'})],
        });
        content.dispatchEvent(event);
        expect(event.defaultPrevented).toBe(true);
        expect(content.innerHTML).toBe('');
        expect(onChange).not.toHaveBeenCalled();
    });

    it('allows plain text alongside a zero-size image placeholder', () => {
        content.dispatchEvent(transferEvent('paste', 'Text', {
            types: ['Files', 'text/plain'], files: [new File([], 'image.png', {type: 'image/png'})],
        }));
        expect(content.innerHTML).toBe('Text');
    });

    it('removes unsupported media already in the note and reports the cleanup', () => {
        content.innerHTML = '<p>Note</p><img src="image.png"><video></video><iframe></iframe>';
        content.dispatchEvent(transferEvent('paste', '', {types: ['text/html']}));
        expect(content.innerHTML).toBe('<p>Note</p>');
        expect(onChange).toHaveBeenLastCalledWith('<p>Note</p>');
    });

    it('does not insert clipboard HTML when plain text is absent', () => {
        const event = transferEvent('paste', '', {types: ['text/html']});
        content.dispatchEvent(event);
        expect(event.defaultPrevented).toBe(true);
        expect(content.innerHTML).toBe('');
        expect(document.execCommand).not.toHaveBeenCalled();
    });

    it('inserts dropped text without its foreign HTML formatting', () => {
        const event = transferEvent('drop', 'First <tag>\nSecond');
        content.dispatchEvent(event);
        expect(event.defaultPrevented).toBe(true);
        expect(content.innerHTML).toBe('First &lt;tag&gt;<br>Second');
        expect(onChange).toHaveBeenLastCalledWith(content.innerHTML);
    });

    it.each(['image/png', 'text/plain'])('blocks dropped files of type %s', type => {
        const event = transferEvent('drop', 'File contents', {
            types: ['Files', 'text/plain'], files: [new File(['file'], 'file', {type})],
        });
        content.dispatchEvent(event);
        expect(event.defaultPrevented).toBe(true);
        expect(content.innerHTML).toBe('');
    });

    it('does not insert rich-only drops', () => {
        const event = transferEvent('drop', '', {types: ['text/html']});
        content.dispatchEvent(event);
        expect(event.defaultPrevented).toBe(true);
        expect(content.innerHTML).toBe('');
    });
});
