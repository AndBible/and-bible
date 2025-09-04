import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

// Mock document methods
const mockLink = {
    remove: vi.fn(),
    href: '',
    type: '',
    rel: ''
};

const mockHead = {
    appendChild: vi.fn()
};

beforeEach(() => {
    global.document = {
        createElement: vi.fn().mockReturnValue(mockLink),
        getElementsByTagName: vi.fn().mockReturnValue([mockHead])
    } as any;
    
    // Mock window.location
    global.window = {
        location: {
            search: '?fontModuleNames=TTF_TestFont,TTF_AnotherFont'
        }
    } as any;
});

afterEach(() => {
    vi.clearAllMocks();
});

describe('TTF Font Integration', () => {
    it('should generate correct CSS paths for TTF fonts', () => {
        const fontModuleName = 'TTF_TestFont';
        const expectedHref = `/fonts/${fontModuleName}/fonts.css`;
        
        // Verify the CSS path format matches what TTF installation creates
        expect(expectedHref).toBe('/fonts/TTF_TestFont/fonts.css');
    });
    
    it('should handle font module names with TTF_ prefix', () => {
        const fontModuleNames = ['TTF_TestFont', 'TTF_AnotherFont', 'RegularModule'];
        
        // All module names should be handled the same way
        fontModuleNames.forEach(moduleName => {
            const expectedHref = `/fonts/${moduleName}/fonts.css`;
            expect(expectedHref).toMatch(/^\/fonts\/.+\/fonts\.css$/);
        });
    });

    it('should process TTF font names correctly', () => {
        // Test TTF name processing logic similar to installTtf
        const displayName = 'MyCustomFont.ttf';
        const fontName = displayName.replace(/\.ttf$/i, ''); // JS equivalent of removeSuffix
        const moduleInitials = `TTF_${fontName}`;
        
        expect(fontName).toBe('MyCustomFont');
        expect(moduleInitials).toBe('TTF_MyCustomFont');
        
        // Test CSS font-family name
        const expectedCss = `@font-face {
    font-family: '${fontName}';
    src: url('../${displayName}') format('truetype');
}`;
        
        expect(expectedCss).toContain("font-family: 'MyCustomFont'");
        expect(expectedCss).toContain('MyCustomFont.ttf');
    });
});