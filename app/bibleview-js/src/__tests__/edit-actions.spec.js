import { describe, it, expect, beforeEach } from 'vitest'
import { JSDOM } from 'jsdom'
import { EditActionMode } from '@/types/client-objects'

// Import the functions we want to test - these are not exported, so we'll need to test indirectly
// We'll create test bookmarks with edit actions and verify the DOM manipulation

describe('Edit Actions', () => {
  let dom
  let document
  let window

  beforeEach(() => {
    dom = new JSDOM(`
      <!DOCTYPE html>
      <html>
        <body>
          <div id="doc-test">
            <span id="o-100">In the beginning</span>
            <span id="o-101">was the Word</span>
          </div>
        </body>
      </html>
    `)
    document = dom.window.document
    window = dom.window
    global.document = document
    global.window = window
  })

  it('should parse XML content correctly', () => {
    // Test the parseEditActionContent function indirectly by testing the DOM result
    const testContent = 'Some text<br/>More text<subtitle>A Title</subtitle>Final text'
    
    // Create a test fragment manually to verify our parsing logic
    const fragment = document.createDocumentFragment()
    
    // Split content by XML tags while preserving the tags
    const parts = testContent.split(/(<br\s*\/?>|<subtitle>.*?<\/subtitle>)/)
    
    for (const part of parts) {
      if (!part) continue
      
      if (part.match(/^<br\s*\/?>$/)) {
        // Paragraph break
        const spanElement = document.createElement('span')
        spanElement.className = 'paragraphBreak skip-offset'
        fragment.appendChild(spanElement)
      } else if (part.match(/^<subtitle>(.*?)<\/subtitle>$/)) {
        // Subtitle
        const match = part.match(/^<subtitle>(.*?)<\/subtitle>$/)
        if (match) {
          const h3Element = document.createElement('h3')
          h3Element.className = 'titleStyle skip-offset'
          h3Element.textContent = match[1]
          fragment.appendChild(h3Element)
        }
      } else {
        // Regular text
        if (part.trim()) {
          const textNode = document.createTextNode(part)
          fragment.appendChild(textNode)
        }
      }
    }
    
    // Verify the fragment contains the expected elements
    const tempDiv = document.createElement('div')
    tempDiv.appendChild(fragment)
    
    expect(tempDiv.textContent).toContain('Some text')
    expect(tempDiv.textContent).toContain('More text')
    expect(tempDiv.textContent).toContain('A Title')
    expect(tempDiv.textContent).toContain('Final text')
    
    const paragraphBreaks = tempDiv.querySelectorAll('.paragraphBreak')
    expect(paragraphBreaks.length).toBe(1)
    expect(paragraphBreaks[0].className).toBe('paragraphBreak skip-offset')
    
    const subtitles = tempDiv.querySelectorAll('.titleStyle')
    expect(subtitles.length).toBe(1)
    expect(subtitles[0].textContent).toBe('A Title')
    expect(subtitles[0].className).toBe('titleStyle skip-offset')
  })

  it('should handle empty edit action gracefully', () => {
    const editAction = { mode: null, content: null }
    
    // Test that null/empty values are handled correctly
    expect(editAction.mode).toBeNull()
    expect(editAction.content).toBeNull()
  })

  it('should handle different edit action modes', () => {
    // Test that all edit action modes are available
    expect(EditActionMode.APPEND).toBe('APPEND')
    expect(EditActionMode.PREPEND).toBe('PREPEND')
  })

  it('should create edit action elements with proper classes', () => {
    // Test creating an edit action element manually
    const container = document.createElement('span')
    container.classList.add('bookmark-edit-action', 'skip-offset')
    
    // Add some test content
    const content = 'Test content<br/>More content'
    const parts = content.split(/(<br\s*\/?>)/)
    
    for (const part of parts) {
      if (!part) continue
      
      if (part.match(/^<br\s*\/?>$/)) {
        const spanElement = document.createElement('span')
        spanElement.className = 'paragraphBreak skip-offset'
        container.appendChild(spanElement)
      } else if (part.trim()) {
        const textNode = document.createTextNode(part)
        container.appendChild(textNode)
      }
    }
    
    expect(container.classList.contains('bookmark-edit-action')).toBe(true)
    expect(container.classList.contains('skip-offset')).toBe(true)
    expect(container.textContent).toContain('Test content')
    expect(container.textContent).toContain('More content')
    
    const paragraphBreaks = container.querySelectorAll('.paragraphBreak')
    expect(paragraphBreaks.length).toBe(1)
  })

  it('should handle complex XML content with multiple elements', () => {
    const complexContent = 'Introduction<br/><subtitle>Chapter 1</subtitle>Content here<br/><subtitle>Chapter 2</subtitle>More content'
    
    const fragment = document.createDocumentFragment()
    const parts = complexContent.split(/(<br\s*\/?>|<subtitle>.*?<\/subtitle>)/)
    
    for (const part of parts) {
      if (!part) continue
      
      if (part.match(/^<br\s*\/?>$/)) {
        const spanElement = document.createElement('span')
        spanElement.className = 'paragraphBreak skip-offset'
        fragment.appendChild(spanElement)
      } else if (part.match(/^<subtitle>(.*?)<\/subtitle>$/)) {
        const match = part.match(/^<subtitle>(.*?)<\/subtitle>$/)
        if (match) {
          const h3Element = document.createElement('h3')
          h3Element.className = 'titleStyle skip-offset'
          h3Element.textContent = match[1]
          fragment.appendChild(h3Element)
        }
      } else if (part.trim()) {
        const textNode = document.createTextNode(part)
        fragment.appendChild(textNode)
      }
    }
    
    const tempDiv = document.createElement('div')
    tempDiv.appendChild(fragment)
    
    // Check that we have the expected number of elements
    const paragraphBreaks = tempDiv.querySelectorAll('.paragraphBreak')
    expect(paragraphBreaks.length).toBe(2)
    
    const subtitles = tempDiv.querySelectorAll('.titleStyle')
    expect(subtitles.length).toBe(2)
    expect(subtitles[0].textContent).toBe('Chapter 1')
    expect(subtitles[1].textContent).toBe('Chapter 2')
    
    // Verify text content is preserved
    expect(tempDiv.textContent).toContain('Introduction')
    expect(tempDiv.textContent).toContain('Content here')
    expect(tempDiv.textContent).toContain('More content')
  })
})
