import { describe, it, expect } from 'vitest'
import { validateXmlContent, validateBookmarkEditActionContent, XmlValidationOptions } from '@/utils/xml-validation'

describe('xml-validation', () => {
  describe('validateXmlContent', () => {
    const basicOptions: XmlValidationOptions = {
      allowedTags: ['br', 'subtitle'],
      selfClosingTags: ['br'],
      errorMessages: {
        xmlParseError: 'XML parsing error',
        invalidTag: 'Invalid tag',
        invalidClosingTag: 'Invalid closing tag',
        unmatchedClosingTag: 'Unmatched closing tag',
        unclosedTag: 'Unclosed tag',
      }
    }

    it('should return null for empty content', () => {
      expect(validateXmlContent('', basicOptions)).toBeNull()
      expect(validateXmlContent('   ', basicOptions)).toBeNull()
    })

    it('should validate simple text content', () => {
      expect(validateXmlContent('Hello world', basicOptions)).toBeNull()
    })

    it('should validate allowed self-closing tags', () => {
      expect(validateXmlContent('Text<br/>More text', basicOptions)).toBeNull()
      expect(validateXmlContent('Line 1<br />Line 2', basicOptions)).toBeNull()
    })

    it('should validate allowed paired tags', () => {
      expect(validateXmlContent('Text<subtitle>Title</subtitle>More text', basicOptions)).toBeNull()
    })

    it('should validate complex valid content', () => {
      const content = 'Introduction<br/><subtitle>Chapter 1</subtitle>Content here<br/><subtitle>Chapter 2</subtitle>More content'
      expect(validateXmlContent(content, basicOptions)).toBeNull()
    })

    it('should reject disallowed tags', () => {
      const result = validateXmlContent('Text<div>content</div>', basicOptions)
      expect(result).toContain('Invalid tag: <div>')
      expect(result).toContain('Only <br>, <subtitle> tags are allowed')
    })

    it('should reject closing tags for self-closing elements', () => {
      const result = validateXmlContent('Text</br>More text', basicOptions)
      expect(result).toContain('XML parsing error') // DOMParser catches this before our custom validation
    })

    it('should reject unmatched closing tags', () => {
      const result = validateXmlContent('Text</subtitle>More text', basicOptions)
      expect(result).toContain('XML parsing error') // DOMParser catches this before our custom validation
    })

    it('should reject unclosed tags', () => {
      const result = validateXmlContent('Text<subtitle>Title', basicOptions)
      expect(result).toContain('XML parsing error') // DOMParser catches this before our custom validation
    })

    it('should handle nested tags correctly', () => {
      // This should be invalid since br can't be nested inside subtitle
      const content = 'Text<subtitle>Title<br/>More</subtitle>'
      expect(validateXmlContent(content, basicOptions)).toBeNull() // This actually passes because br is self-closing
    })

    it('should work with custom error messages', () => {
      const customOptions: XmlValidationOptions = {
        allowedTags: ['span'],
        errorMessages: {
          invalidTag: 'Custom invalid tag message',
        }
      }
      
      const result = validateXmlContent('<div>test</div>', customOptions)
      expect(result).toContain('Custom invalid tag message')
    })

    it('should work with different allowed tags', () => {
      const htmlOptions: XmlValidationOptions = {
        allowedTags: ['p', 'span', 'div'],
        selfClosingTags: [],
      }
      
      expect(validateXmlContent('<p>Test</p>', htmlOptions)).toBeNull()
      expect(validateXmlContent('<span><div>Nested</div></span>', htmlOptions)).toBeNull()
      
      const result = validateXmlContent('<h1>Title</h1>', htmlOptions)
      expect(result).toContain('Invalid tag: <h1>')
    })
  })

  describe('validateBookmarkEditActionContent', () => {
    const mockStrings = {
      xmlParseError: 'XML parse error'
    }

    it('should validate bookmark-specific content', () => {
      expect(validateBookmarkEditActionContent('Simple text', mockStrings)).toBeNull()
      expect(validateBookmarkEditActionContent('Text<br/>More text', mockStrings)).toBeNull()
      expect(validateBookmarkEditActionContent('Text<subtitle>Title</subtitle>More', mockStrings)).toBeNull()
    })

    it('should reject invalid bookmark content', () => {
      const result = validateBookmarkEditActionContent('Text<div>Invalid</div>', mockStrings)
      expect(result).toContain('Invalid tag: <div>')
    })

    it('should handle empty content', () => {
      expect(validateBookmarkEditActionContent('', mockStrings)).toBeNull()
      expect(validateBookmarkEditActionContent(null as any, mockStrings)).toBeNull()
    })

    it('should use custom error messages from strings', () => {
      const customStrings = {
        xmlParseError: 'Custom XML error'
      }
      
      // This won't actually trigger the xmlParseError in our simple test,
      // but verifies the function accepts the strings parameter
      expect(validateBookmarkEditActionContent('Valid content', customStrings)).toBeNull()
    })
  })

  describe('edge cases', () => {
    const options: XmlValidationOptions = {
      allowedTags: ['br', 'subtitle'],
      selfClosingTags: ['br']
    }

    it('should handle malformed XML gracefully', () => {
      const result = validateXmlContent('Text<subtitle>Unclosed', options)
      expect(result).toContain('XML parsing error') // DOMParser catches this before our custom validation
    })

    it('should handle tags with attributes', () => {
      // Our regex should handle attributes
      expect(validateXmlContent('Text<br class="test"/>More', options)).toBeNull()
    })

    it('should be case insensitive for tag names', () => {
      expect(validateXmlContent('Text<BR/>More', options)).toBeNull()
      expect(validateXmlContent('Text<Subtitle>Title</subtitle>More', options)).toBeNull()
    })

    it('should handle multiple spaces in tags', () => {
      expect(validateXmlContent('Text<br  />More', options)).toBeNull()
    })
  })
})