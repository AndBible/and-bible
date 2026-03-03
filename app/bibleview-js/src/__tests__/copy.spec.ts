import {describe, it, expect, vi, beforeAll, beforeEach, afterEach} from 'vitest'
import {mount, VueWrapper} from '@vue/test-utils'
import BibleView from '@/components/BibleView.vue'
import {BibleDocumentType} from '@/types/documents'
import { nextTick } from 'vue'

function selectText(node: Node) {
    const range = document.createRange()
    range.selectNodeContents(node)
    const sel = window.getSelection()!
    sel.removeAllRanges()
    sel.addRange(range)
}

function dispatchCtrlC() {
    document.dispatchEvent(new KeyboardEvent('keydown', {
        ctrlKey: true,
        code: 'KeyC',
        key: 'c',
        bubbles: true,
    }))
}

describe('copy functionality', () => {
    let wrapper: VueWrapper<any>

    beforeAll(async () => {
        window.bibleView = {}
        window.bibleViewDebug = {}

        const androidMock: Record<string, any> = {
            getActiveLanguages: vi.fn(() => JSON.stringify(['en'])),
        }
        window.android = new Proxy(androidMock as any, {
            get(target, prop) {
                if (typeof prop === 'string' && !(prop in target)) {
                    target[prop] = vi.fn()
                }
                return target[prop as string]
            }
        }) as any
    })
    
    beforeEach(async () => {
        window.getSelection()?.removeAllRanges()
        vi.mocked(window.android.copyText).mockClear()
        vi.mocked(window.android.copyVerse).mockClear()
        wrapper = mount(BibleView, {attachTo: document.body})
        await nextTick()
    })

    afterEach(() => {
        wrapper?.unmount()
    })

    describe('issue #3462 - copy from modal/footnote', () => {
        it('copies selected text from inside #modals div', () => {
            const modalsDiv = document.getElementById('modals')!
            const p = document.createElement('p')
            p.textContent = 'Footnote text to copy'
            modalsDiv.appendChild(p)

            selectText(p)
            dispatchCtrlC()

            expect(window.android.copyText).toHaveBeenCalledWith('Footnote text to copy')
            expect(window.android.copyVerse).not.toHaveBeenCalled()
        })

        it('does not copy when modal selection is empty', () => {
            const modalsDiv = document.getElementById('modals')!
            const p = document.createElement('p')
            p.textContent = ''
            modalsDiv.appendChild(p)

            dispatchCtrlC()

            expect(window.android.copyText).not.toHaveBeenCalled()
        })
    })

    describe('issue #2860 - copy selected text in non-bible document', () => {
        let p1: HTMLParagraphElement
        let p2: HTMLParagraphElement

        beforeEach(() => {
            const content = document.getElementById('content')!
            p1 = document.createElement('p')
            p1.textContent = 'Selected paragraph'
            p2 = document.createElement('p')
            p2.textContent = 'Not selected paragraph'
            content.appendChild(p1)
            content.appendChild(p2)
        })

        it('copies only the selected text, not all content', () => {
            selectText(p1)
            dispatchCtrlC()

            expect(window.android.copyText).toHaveBeenCalledWith('Selected paragraph')
        })

        it('copies selected text when selection is not in modal', () => {
            const modalsDiv = document.getElementById('modals')!
            const p = document.createElement('p')
            p.textContent = 'Modal text to not copy'
            modalsDiv.appendChild(p)

            selectText(p1)
            dispatchCtrlC()

            expect(window.android.copyText).toHaveBeenCalledWith('Selected paragraph')
        })

        it('copies selected text when selection is in modal', () => {
            const modalsDiv = document.getElementById('modals')!
            const p = document.createElement('p')
            p.textContent = 'Modal text to copy'
            modalsDiv.appendChild(p)

            selectText(p)
            dispatchCtrlC()

            expect(window.android.copyText).toHaveBeenCalledWith('Modal text to copy')
        })

        it('does not copy when no text is selected', () => {
            dispatchCtrlC()

            expect(window.android.copyText).not.toHaveBeenCalled()
            expect(window.android.copyVerse).not.toHaveBeenCalled()
        })
    })

    describe('bible document - copyVerse path', () => {
        const bibleDoc: BibleDocumentType = {
            id: 'test-bible',
            type: 'bible',
            bookmarks: [],
            bibleBookName: 'Genesis',
            addChapter: false,
            chapterNumber: 1,
            originalOrdinalRange: [0, 1],
            osisFragment: {
                xml: '<verse verseOrdinal="0" osisID="Gen.1.1">In the beginning God created the heaven and the earth.</verse>',
                key: 'Gen.1',
                keyName: 'Genesis 1',
                v11n: 'KJV',
                bookCategory: 'BIBLE',
                bookInitials: 'KJV',
                bookAbbreviation: 'KJV',
                osisRef: 'Gen.1.1',
                isNewTestament: false,
                features: {},
                ordinalRange: [0, 1],
                language: 'en',
                direction: 'ltr',
                hasStrongs: false,
            },
            bookInitials: 'KJV',
            bookCategory: 'BIBLE',
            bookAbbreviation: 'KJV',
            bookName: 'King James Version',
            key: 'Gen.1',
            v11n: 'KJV',
            osisRef: 'Gen.1.1',
            annotateRef: 'Gen.1.1',
            genericBookmarks: [],
            ordinalRange: [0, 1],
            isEpub: false,
        }

        beforeEach(async () => {
            window.bibleViewDebug.documents.push(bibleDoc)
            await nextTick()
        })

        it('calls copyVerse when selecting text in a bible verse', () => {
            const verseEl = document.querySelector('.ordinal')
            expect(verseEl).not.toBeNull()

            selectText(verseEl!)
            dispatchCtrlC()

            expect(window.android.copyVerse).toHaveBeenCalledWith('KJV', 0, -1)
            expect(window.android.copyText).not.toHaveBeenCalled()
        })

        it('calls copyText when selecting text in a modal', () => {
            const modalsDiv = document.getElementById('modals')!
            const p = document.createElement('p')
            p.textContent = 'Modal text to copy'
            modalsDiv.appendChild(p)

            const verseEl = document.querySelector('.ordinal')
            expect(verseEl).not.toBeNull()

            selectText(p)
            dispatchCtrlC()

            expect(window.android.copyVerse).not.toHaveBeenCalled()
            expect(window.android.copyText).toHaveBeenCalledWith('Modal text to copy')
        })
    })
})
