/**
 * XML validation utility functions
 */

export interface XmlValidationOptions {
    allowedTags: string[];
    selfClosingTags?: string[];
    errorMessages: Partial<ErrorStrings>;
}

/**
 * Validates XML content with configurable allowed tags and validation rules
 * @param content The XML content to validate
 * @param options Validation options including allowed tags and error messages
 * @returns null if valid, error message string if invalid
 */
export function validateXmlContent(content: string, options: XmlValidationOptions): string | null {
    if (content.trim() === '') {
        return null; // Empty content is valid
    }
    
    const {
        allowedTags,
        selfClosingTags = [],
        errorMessages = {}
    } = options;
    
    try {
        const wrappedContent = `<root>${content}</root>`;
        
        const parser = new DOMParser();
        const doc = parser.parseFromString(wrappedContent, 'application/xml');
        
        const parserError = doc.querySelector('parsererror');
        if (parserError) {
            // Extract error message from parser error
            const errorText = parserError.textContent || '';
            const xmlParseError = errorMessages.xmlParseError;
            return `${xmlParseError}: ${errorText}`;
        }
        
        // Additional validation: check for balanced tags and allowed tags
        const tagRegex = /<(\/?)([\w-]+)(?:\s[^>]*)?>/gi;
        const tagStack: string[] = [];
        let match;
        
        while ((match = tagRegex.exec(content)) !== null) {
            const isClosing = match[1] === '/';
            const tagName = match[2].toLowerCase();
            
            if (!allowedTags.includes(tagName)) {
                const invalidTag = errorMessages.invalidTag;
                return `${invalidTag}: <${tagName}>. Only <${allowedTags.join('>, <')}> tags are allowed.`;
            }
            
            if (selfClosingTags.includes(tagName)) {
                // Self-closing tags should not have closing tags
                if (isClosing) {
                    const invalidClosingTag = errorMessages.invalidClosingTag;
                    return `${invalidClosingTag}: </${tagName}>. Use <${tagName}/> instead.`;
                }
            } else {
                // Non-self-closing tags should be properly balanced
                if (isClosing) {
                    if (tagStack.length === 0 || tagStack[tagStack.length - 1] !== tagName) {
                        const unmatchedClosingTag = errorMessages.unmatchedClosingTag;
                        return `${unmatchedClosingTag}: </${tagName}>`;
                    }
                    tagStack.pop();
                } else {
                    tagStack.push(tagName);
                }
            }
        }
        
        // Check for unclosed tags
        if (tagStack.length > 0) {
            const unclosedTag = errorMessages.unclosedTag;
            return `${unclosedTag}: <${tagStack[tagStack.length - 1]}>`;
        }
        
        return null; // Valid XML
    } catch (error) {
        const xmlParseError = errorMessages.xmlParseError;
        return `${xmlParseError}: ${error instanceof Error ? error.message : 'Unknown error'}`;
    }
}

type ErrorStrings = {
    xmlParseError: string;
    invalidTag: string;
    invalidClosingTag: string;
    unmatchedClosingTag: string;
    unclosedTag: string;
}

/**
 * Validates bookmark edit action content (convenience function)
 * @param content The content to validate
 * @param strings Translation strings for error messages
 * @returns null if valid, error message string if invalid
 */
export function validateBookmarkEditActionContent(content: string, strings: ErrorStrings): string | null {
    return validateXmlContent(content, {
        allowedTags: ['br', 'subtitle'],
        selfClosingTags: ['br'],
        errorMessages: {
            xmlParseError: strings.xmlParseError,
            invalidTag: strings.invalidTag,
            invalidClosingTag: strings.invalidClosingTag,
            unmatchedClosingTag: strings.unmatchedClosingTag,
            unclosedTag: strings.unclosedTag,
        }
    });
}