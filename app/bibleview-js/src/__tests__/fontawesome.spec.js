import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome'
import { icon } from '@fortawesome/fontawesome-svg-core'
import { faBrain, faParagraph, faRobot } from '@fortawesome/free-solid-svg-icons'
import { customIconMap, useFontAwesome } from '@/composables/fontawesome'

describe('FontAwesome integration', () => {
  it('renders imported icon objects through the real Vue component', () => {
    for (const definition of [faBrain, faParagraph, faRobot]) {
      const wrapper = mount(FontAwesomeIcon, { props: { icon: definition } })
      expect(wrapper.find('svg').attributes('data-icon')).toBe(definition.iconName)
      expect(wrapper.find('path').attributes('d')).toBeTruthy()
      wrapper.unmount()
    }
  })

  it('preserves registered legacy aliases and custom action icons', () => {
    useFontAwesome()
    for (const name of [
      'bookmark', 'edit', 'file-alt', 'info-circle', 'share-alt', 'pen-square',
      'arrows-alt-v', 'ellipsis-h', 'headphones', 'clock',
      'custom-compare', 'custom-morph', 'custom-whole-verse-false', 'custom-whole-verse-true',
    ]) {
      const result = icon({ prefix: 'fas', iconName: name })
        ?? icon({ prefix: 'far', iconName: name })
      expect(result, name).toBeDefined()
      expect(result.html.join(''), name).toContain('<path')
    }
  })

  it('keeps every custom label icon renderable', () => {
    expect(customIconMap.size).toBe(32)
    for (const [name, result] of customIconMap) {
      expect(result.html.join(''), name).toContain('<path')
    }
  })
})
