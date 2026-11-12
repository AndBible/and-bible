import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {init} from '../lib/pell/pell';

describe('note editor paste', () => {
    let content;
    let commands;

    beforeEach(() => {
        commands = [];
        document.execCommand = vi.fn((command, _ui, value) => {
            commands.push([command, value]);
            return true;
        });
        const element = document.createElement('div');
        document.body.appendChild(element);
        init({element, actions: [], onChange: () => {}});
        content = element.content;
        commands.length = 0;
    });

    afterEach(() => {
        document.body.innerHTML = '';
        vi.restoreAllMocks();
    });

    it('inserts clipboard text without source HTML and preserves line breaks', () => {
        const event = new Event('paste', {bubbles: true, cancelable: true});
        Object.defineProperty(event, 'clipboardData', {value: {
            types: ['text/plain', 'text/html'], files: [],
            getData: type => type === 'text/plain' ? 'First <tag>\nSecond\n\nFourth' : '<span style="font-size:40px">First</span>',
        }});
        content.dispatchEvent(event);
        expect(event.defaultPrevented).toBe(true);
        expect(commands).toEqual([['insertText', 'First <tag>\nSecond\n\nFourth']]);
    });

    it('continues blocking image file pastes', () => {
        const event = new Event('paste', {bubbles: true, cancelable: true});
        Object.defineProperty(event, 'clipboardData', {value: {
            types: ['Files'], files: [new File(['image'], 'image.png', {type: 'image/png'})],
            getData: () => '',
        }});
        content.dispatchEvent(event);
        expect(event.defaultPrevented).toBe(true);
        expect(commands).toEqual([]);
    });

    it('reports removed media when no plain text is available', () => {
        const onChange = vi.fn();
        const element = document.createElement('div');
        document.body.appendChild(element);
        init({element, actions: [], onChange});
        element.content.innerHTML = '<p>Note</p><img src="image.png">';
        const event = new Event('paste', {bubbles: true, cancelable: true});
        Object.defineProperty(event, 'clipboardData', {value: {
            types: ['text/html'], files: [], getData: () => '',
        }});
        element.content.dispatchEvent(event);
        expect(onChange).toHaveBeenCalledWith('<p>Note</p>');
    });

    it('does not insert clipboard HTML when plain text is absent', () => {
        const event = new Event('paste', {bubbles: true, cancelable: true});
        Object.defineProperty(event, 'clipboardData', {value: {
            types: ['text/html'], files: [], getData: () => '',
        }});
        content.dispatchEvent(event);
        expect(event.defaultPrevented).toBe(true);
        expect(commands).toEqual([]);
    });
});
