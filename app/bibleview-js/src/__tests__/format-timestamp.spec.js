/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

import {describe, it, expect} from 'vitest'
import {useCommon} from '@/composables'
import {createApp} from 'vue'

// Mock the necessary Vue composables
function createMockApp() {
    const app = createApp({
        setup() {
            return useCommon()
        }
    })
    
    // Mock the required injections
    app.provide('config', {})
    app.provide('appSettings', {})
    app.provide('calculatedConfig', {})
    app.provide('android', {})
    app.provide('strings', {})
    
    return app
}

describe('formatTimestamp', () => {
    it('should include abbreviated weekday in formatted date', () => {
        const app = createMockApp()
        const vm = app.mount(document.createElement('div'))
        
        // Test with a known date: Sunday, January 1, 2023 12:00:00 PM
        const sunday = new Date('2023-01-01T12:00:00').getTime()
        
        const formatted = vm.formatTimestamp(sunday)
        
        // Should contain the abbreviated weekday
        expect(formatted).toMatch(/Sun/)
        // Should still contain other date components
        expect(formatted).toMatch(/2023/)
        expect(formatted).toMatch(/1/)
        expect(formatted).toMatch(/12/)
        
        app.unmount()
    })
    
    it('should format different weekdays correctly', () => {
        const app = createMockApp()
        const vm = app.mount(document.createElement('div'))
        
        // Test different days of the week
        const testDates = [
            { date: '2023-01-01T12:00:00', expectedDay: 'Sun' }, // Sunday
            { date: '2023-01-02T12:00:00', expectedDay: 'Mon' }, // Monday
            { date: '2023-01-03T12:00:00', expectedDay: 'Tue' }, // Tuesday
            { date: '2023-01-04T12:00:00', expectedDay: 'Wed' }, // Wednesday
            { date: '2023-01-05T12:00:00', expectedDay: 'Thu' }, // Thursday
            { date: '2023-01-06T12:00:00', expectedDay: 'Fri' }, // Friday
            { date: '2023-01-07T12:00:00', expectedDay: 'Sat' }, // Saturday
        ]
        
        testDates.forEach(({ date, expectedDay }) => {
            const timestamp = new Date(date).getTime()
            const formatted = vm.formatTimestamp(timestamp)
            expect(formatted).toMatch(new RegExp(expectedDay))
        })
        
        app.unmount()
    })
    
    it('should maintain backward compatibility with existing format', () => {
        const app = createMockApp()
        const vm = app.mount(document.createElement('div'))
        
        const timestamp = new Date('2023-06-15T14:30:00').getTime()
        const formatted = vm.formatTimestamp(timestamp)
        
        // Should still include year, month, day, hour, minute
        expect(formatted).toMatch(/2023/)
        expect(formatted).toMatch(/6/)
        expect(formatted).toMatch(/15/)
        expect(formatted).toMatch(/14|2/) // 14 (24-hour) or 2 (12-hour PM)
        expect(formatted).toMatch(/30/)
        
        app.unmount()
    })
})