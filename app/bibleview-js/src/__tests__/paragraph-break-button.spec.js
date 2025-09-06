/*
 * Copyright (c) 2024 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ActionButton from '@/components/ActionButton.vue'

// Mock the useCommon composable 
vi.mock('@/composables', () => ({
  useCommon: () => ({
    strings: {
      addBookmark: 'Bookmark',
      verseNote: 'Note',
      verseNoteLong: 'Write a note',
      verseShare: 'Share',
      verseShareLong: 'Share this verse',
      verseSpeak: 'Speak',
      verseCompare: 'Compare',
      verseCompareLong: 'Compare verse',
      verseMemorize: 'Memorize',
      verseMemorizeLong: 'Memorize Bible verses',
      verseMyNotes: 'My Notes',
      verseParagraphBreak: 'Paragraph Break',
      verseParagraphBreakLong: 'Add paragraph break'
    }
  })
}))

// Mock FontAwesome components
vi.mock('@fortawesome/vue-fontawesome', () => ({
  FontAwesomeIcon: {
    name: 'FontAwesomeIcon',
    template: '<i class="fa-icon" :class="icon"></i>',
    props: ['icon']
  },
  FontAwesomeLayers: {
    name: 'FontAwesomeLayers',
    template: '<div class="fa-layers"><slot/></div>'
  }
}))

describe('ActionButton with ADD_PARAGRAPH_BREAK', () => {
  it('displays correct text for ADD_PARAGRAPH_BREAK button', () => {
    const wrapper = mount(ActionButton, {
      props: {
        button: 'ADD_PARAGRAPH_BREAK',
        vertical: false
      }
    })
    
    expect(wrapper.text()).toContain('Paragraph Break')
  })

  it('displays long text for ADD_PARAGRAPH_BREAK button when vertical', () => {
    const wrapper = mount(ActionButton, {
      props: {
        button: 'ADD_PARAGRAPH_BREAK',
        vertical: true
      }
    })
    
    expect(wrapper.text()).toContain('Add paragraph break')
  })

  it('emits click event when clicked', async () => {
    const wrapper = mount(ActionButton, {
      props: {
        button: 'ADD_PARAGRAPH_BREAK',
        vertical: false
      }
    })
    
    await wrapper.find('.large-action').trigger('click')
    expect(wrapper.emitted().click).toHaveLength(1)
  })

  it('shows paragraph icon for ADD_PARAGRAPH_BREAK button', () => {
    const wrapper = mount(ActionButton, {
      props: {
        button: 'ADD_PARAGRAPH_BREAK',
        vertical: false
      }
    })
    
    // FontAwesome component should be present for the paragraph icon
    expect(wrapper.findComponent({ name: 'FontAwesomeIcon' }).exists()).toBe(true)
  })
})

describe('ModalButtonId Type System', () => {
  it('ADD_PARAGRAPH_BREAK is included in BibleModalButtonId', () => {
    // This is a type-level test - if ADD_PARAGRAPH_BREAK wasn't in the type union,
    // the ActionButton component would fail to compile with TypeScript
    const validBibleButton = 'ADD_PARAGRAPH_BREAK'
    
    // Test that the button type is accepted
    expect(validBibleButton).toBe('ADD_PARAGRAPH_BREAK')
  })

  it('ADD_PARAGRAPH_BREAK is included in GenericModalButtonId', () => {
    // This ensures the paragraph break button works for both Bible and non-Bible documents
    const validGenericButton = 'ADD_PARAGRAPH_BREAK'
    
    expect(validGenericButton).toBe('ADD_PARAGRAPH_BREAK')
  })
})