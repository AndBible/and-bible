import {describe, it, expect, vi, beforeAll, beforeEach, afterAll, afterEach} from 'vitest'
import {mount, VueWrapper} from '@vue/test-utils'
import {nextTick} from 'vue'
import BibleView from '@/components/BibleView.vue'
import {BibleDocumentType} from '@/types/documents'

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

        wrapper = mount(BibleView, {attachTo: document.body})
        await nextTick()
        await nextTick()
    })

    beforeEach(() => {
        window.getSelection()?.removeAllRanges()
        vi.mocked(window.android.copyText).mockClear()
        vi.mocked(window.android.copyVerse).mockClear()
    })

    afterAll(() => {
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

            p.remove()
        })

        it('does not copy when modal selection is empty', () => {
            const modalsDiv = document.getElementById('modals')!
            const p = document.createElement('p')
            p.textContent = ''
            modalsDiv.appendChild(p)

            dispatchCtrlC()

            expect(window.android.copyText).not.toHaveBeenCalled()

            p.remove()
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

        afterEach(() => {
            p1.remove()
            p2.remove()
        })

        it('copies only the selected text, not all content', () => {
            selectText(p1)
            dispatchCtrlC()

            expect(window.android.copyText).toHaveBeenCalledWith('Selected paragraph')
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

        beforeAll(async () => {
            window.bibleViewDebug.documents.push(bibleDoc)
            await nextTick()
            await nextTick()
            await new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))
            await nextTick()
        })

        afterAll(async () => {
            const docs = window.bibleViewDebug.documents
            const idx = docs.indexOf(bibleDoc)
            if (idx !== -1) docs.splice(idx, 1)
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
    })
})
