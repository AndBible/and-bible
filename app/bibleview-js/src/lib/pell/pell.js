/*
 * Copyright (c) 2022-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

/* eslint-disable */
const defaultParagraphSeparatorString = 'defaultParagraphSeparator'
export const formatBlock = 'formatBlock'
const addEventListener = (parent, type, listener) => parent.addEventListener(type, listener)
const appendChild = (parent, child) => parent.appendChild(child)
const createElement = tag => document.createElement(tag)
export const queryCommandState = command => document.queryCommandState(command)
export const queryCommandValue = command => document.queryCommandValue(command)

export const exec = (command, value = null) => document.execCommand(command, false, value)

const defaultActions = {
  bold: {
    icon: '<b>B</b>',
    title: 'Bold',
    state: () => queryCommandState('bold'),
    result: () => exec('bold')
  },
  italic: {
    icon: '<i>I</i>',
    title: 'Italic',
    state: () => queryCommandState('italic'),
    result: () => exec('italic')
  },
  underline: {
    icon: '<u>U</u>',
    title: 'Underline',
    state: () => queryCommandState('underline'),
    result: () => exec('underline')
  },
  strikethrough: {
    icon: '<strike>S</strike>',
    title: 'Strike-through',
    state: () => queryCommandState('strikeThrough'),
    result: () => exec('strikeThrough')
  },
  heading1: {
    icon: '<b>H<sub>1</sub></b>',
    title: 'Heading 1',
    result: () => exec(formatBlock, '<h1>')
  },
  heading2: {
    icon: '<b>H<sub>2</sub></b>',
    title: 'Heading 2',
    result: () => exec(formatBlock, '<h2>')
  },
  paragraph: {
    icon: '&#182;',
    title: 'Paragraph',
    result: () => exec(formatBlock, '<p>')
  },
  quote: {
    icon: '&#8220; &#8221;',
    title: 'Quote',
    result: () => exec(formatBlock, '<blockquote>')
  },
  olist: {
    icon: '&#35;',
    title: 'Ordered List',
    result: () => exec('insertOrderedList')
  },
  ulist: {
    icon: '&#8226;',
    title: 'Unordered List',
    result: () => exec('insertUnorderedList')
  },
  code: {
    icon: '&lt;/&gt;',
    title: 'Code',
    result: () => exec(formatBlock, '<pre>')
  },
  line: {
    icon: '&#8213;',
    title: 'Horizontal Line',
    result: () => exec('insertHorizontalRule')
  },
  link: {
    icon: '&#128279;',
    title: 'Link',
    result: () => {
      const url = window.prompt('Enter the link URL')
      if (url) exec('createLink', url)
    }
  },
  image: {
    icon: '&#128247;',
    title: 'Image',
    result: () => {
      const url = window.prompt('Enter the image URL')
      if (url) exec('insertImage', url)
    }
  }
}

const defaultClasses = {
  actionbar: 'pell-actionbar',
  button: 'pell-button',
  content: 'pell-content',
  selected: 'pell-button-selected'
}

export const init = settings => {
  const actions = settings.actions
    ? (
      settings.actions.map(action => {
        if (typeof action === 'string') return defaultActions[action]
        else if (defaultActions[action.name]) return { ...defaultActions[action.name], ...action }
        return action
      })
    )
    : Object.keys(defaultActions).map(action => defaultActions[action])

  const classes = { ...defaultClasses, ...settings.classes }

  const defaultParagraphSeparator = settings[defaultParagraphSeparatorString] || 'div'

  const actionbar = createElement('div')
  actionbar.className = classes.actionbar

  const content = settings.element.content = createElement('div')
  content.contentEditable = true
  content.className = classes.content
  content.oninput = ({ target: { firstChild } }) => {
    settings.onChange(content.innerHTML)
  }
  content.onkeydown = event => {
    if (event.key === 'Enter' && queryCommandValue(formatBlock) === 'blockquote') {
      setTimeout(() => exec(formatBlock, `<${defaultParagraphSeparator}>`), 0)
    }
  }
  // Keep a lost/outside selection from inserting into another editable element.
  const editorRange = () => {
    const selection = window.getSelection()
    if (!selection.rangeCount || !content.contains(selection.getRangeAt(0).commonAncestorContainer)) {
      const range = document.createRange()
      range.selectNodeContents(content)
      range.collapse(false)
      selection.removeAllRanges()
      selection.addRange(range)
    }
    return selection.getRangeAt(0)
  }

  const insertPlainText = text => {
    const selection = window.getSelection()
    const range = editorRange().cloneRange()
    content.focus()
    selection.removeAllRanges()
    selection.addRange(range)
    let usedFallback = false
    let finalBreak
    const insert = (command, value = null) => {
      finalBreak = null
      // Stay on the Range path after a failure: native insertion may swallow a terminal BR.
      if (!usedFallback && exec(command, value)) return
      usedFallback = true
      const range = editorRange()
      range.deleteContents()
      // Never parse transferred text as HTML, including when editing commands fail.
      const node = command === 'insertText' ? document.createTextNode(value) : createElement('br')
      range.insertNode(node)
      range.setStartAfter(node)
      range.collapse(true)
      if (command === 'insertParagraph') finalBreak = node
      selection.removeAllRanges()
      selection.addRange(range)
    }
    // Match Enter (defaultParagraphSeparator), not each engine's handling of raw newlines.
    text.split(/\r\n|\r|\n/).forEach((line, index) => {
      if (index) insert('insertParagraph')
      if (line) insert('insertText', line)
    })
    // Range.insertNode can split a text node, leaving invisible empty siblings after the BR.
    while (finalBreak?.nextSibling?.nodeType === Node.TEXT_NODE && !finalBreak.nextSibling.length) {
      finalBreak.nextSibling.remove()
    }
    // A terminal BR alone does not display the final empty line in contenteditable.
    if (finalBreak && !finalBreak.nextSibling) finalBreak.after(createElement('br'))
    // Range mutations do not dispatch the input event that execCommand normally sends.
    if (usedFallback) settings.onChange(content.innerHTML)
  }

  const transferPlainText = (event, data, isDrop = false) => {
    event.preventDefault()
    if (!data) return
    const files = Array.from(data.files || [])
    // Preserve clipboard placeholder support, but never accept dropped files.
    if (isDrop ? files.length : files.some(file => file.type.startsWith('image/') && file.size > 0)) return

    // Clean unsupported media already stored in the note; transferred HTML is never inserted.
    const media = content.querySelectorAll('img, video, audio, embed, object, iframe')
    media.forEach(el => el.remove())
    if (media.length) settings.onChange(content.innerHTML)
    const text = data.getData('text/plain')
    if (text) insertPlainText(text)
  }
  content.onpaste = event => transferPlainText(event, event.clipboardData || window.clipboardData)
  content.ondragover = event => event.preventDefault()
  content.ondrop = event => {
    // Drops target the pointer, not the caret left behind before dragging.
    const position = document.caretPositionFromPoint?.(event.clientX, event.clientY)
    let range
    if (position) {
      range = document.createRange()
      range.setStart(position.offsetNode, position.offset)
      range.collapse(true)
    } else {
      range = document.caretRangeFromPoint?.(event.clientX, event.clientY)
    }
    if (range && content.contains(range.commonAncestorContainer)) {
      const selection = window.getSelection()
      selection.removeAllRanges()
      selection.addRange(range)
    }
    transferPlainText(event, event.dataTransfer, true)
  }
  appendChild(settings.element, content)
  appendChild(settings.element, actionbar)

  actions.forEach(action => {
    if(action.divider) {
      const divider = createElement('div')
      divider.className = "pell-divider";
      appendChild(actionbar, divider)
      return;
    }
    const button = createElement('button')
    button.className = `${classes.button} ${action.class||""}`
    button.innerHTML = action.icon
    button.title = action.title
    button.setAttribute('type', 'button')
    button.onclick = () => action.result() && content.focus()

    if (action.state) {
      const handler = () => button.classList[action.state() ? 'add' : 'remove'](classes.selected)
      addEventListener(content, 'keyup', handler)
      addEventListener(content, 'mouseup', handler)
      addEventListener(button, 'click', handler)
    }

    appendChild(actionbar, button)
  })

  if (settings.styleWithCSS) exec('styleWithCSS')
  exec(defaultParagraphSeparatorString, defaultParagraphSeparator)

  return settings.element
}

export default { exec, init }
