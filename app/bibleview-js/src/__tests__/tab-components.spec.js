import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import TabContainer from '@/components/tabs/TabContainer.vue'
import TabNavigation from '@/components/tabs/TabNavigation.vue'
import TabPanel from '@/components/tabs/TabPanel.vue'

// Mock FontAwesome components
vi.mock('@fortawesome/vue-fontawesome', () => ({
  FontAwesomeIcon: {
    name: 'FontAwesomeIcon',
    template: '<i class="fa-icon" :class="icon"></i>',
    props: ['icon']
  }
}))

// Mock the common SCSS file
vi.mock('@/common.scss', () => ({}))

describe('TabContainer.vue', () => {
  const mockTabs = [
    { id: 'tab1', label: 'Tab 1', icon: 'home' },
    { id: 'tab2', label: 'Tab 2', icon: 'settings' },
    { id: 'tab3', label: 'Tab 3', disabled: true }
  ]

  let wrapper

  beforeEach(() => {
    wrapper = mount(TabContainer, {
      props: {
        tabs: mockTabs
      },
      slots: {
        tab1: '<div class="tab1-content">Tab 1 Content</div>',
        tab2: '<div class="tab2-content">Tab 2 Content</div>',
        tab3: '<div class="tab3-content">Tab 3 Content</div>'
      }
    })
  })

  it('renders correctly with tabs', () => {
    expect(wrapper.exists()).toBe(true)
    expect(wrapper.find('.tab-container').exists()).toBe(true)
    expect(wrapper.find('.tab-content').exists()).toBe(true)
  })

  it('initializes with first non-disabled tab as active', async () => {
    expect(wrapper.vm.getActiveTab()).toBe('tab1')
    
    // Check that the first tab panel is visible
    const tab1Panel = wrapper.find('.tab1-content')
    expect(tab1Panel.exists()).toBe(true)
  })

  it('switches tabs when tab is clicked', async () => {
    const tabNavigation = wrapper.findComponent(TabNavigation)
    
    // Simulate tab change event
    await tabNavigation.vm.$emit('tab-change', 'tab2')
    
    expect(wrapper.vm.getActiveTab()).toBe('tab2')
  })

  it('does not switch to disabled tabs', async () => {
    const tabNavigation = wrapper.findComponent(TabNavigation)
    
    // Try to switch to disabled tab
    await tabNavigation.vm.$emit('tab-change', 'tab3')
    
    // Should still be on first tab
    expect(wrapper.vm.getActiveTab()).toBe('tab1')
  })

  it('emits tabChange event when tab changes', async () => {
    const tabNavigation = wrapper.findComponent(TabNavigation)
    
    await tabNavigation.vm.$emit('tab-change', 'tab2')
    
    expect(wrapper.emitted('tabChange')).toBeTruthy()
    expect(wrapper.emitted('tabChange')[0]).toEqual(['tab2', mockTabs[1]])
  })

  it('respects defaultTab prop', () => {
    const wrapperWithDefault = mount(TabContainer, {
      props: {
        tabs: mockTabs,
        defaultTab: 'tab2'
      },
      slots: {
        tab1: '<div>Tab 1</div>',
        tab2: '<div>Tab 2</div>',
        tab3: '<div>Tab 3</div>'
      }
    })

    expect(wrapperWithDefault.vm.getActiveTab()).toBe('tab2')
  })

  it('hides navigation when showNavigation is false', () => {
    const wrapperNoNav = mount(TabContainer, {
      props: {
        tabs: mockTabs,
        showNavigation: false
      },
      slots: {
        tab1: '<div>Tab 1</div>',
        tab2: '<div>Tab 2</div>',
        tab3: '<div>Tab 3</div>'
      }
    })

    expect(wrapperNoNav.findComponent(TabNavigation).exists()).toBe(false)
  })

  it('applies custom CSS classes', () => {
    const customWrapper = mount(TabContainer, {
      props: {
        tabs: mockTabs,
        containerClass: 'custom-container',
        contentClass: 'custom-content',
        navigationClass: 'custom-navigation',
        panelClass: 'custom-panel'
      },
      slots: {
        tab1: '<div>Tab 1</div>',
        tab2: '<div>Tab 2</div>',
        tab3: '<div>Tab 3</div>'
      }
    })

    expect(customWrapper.find('.tab-container').classes()).toContain('custom-container')
    expect(customWrapper.find('.tab-content').classes()).toContain('custom-content')
  })

  it('provides correct slot props', () => {
    const wrapperWithProps = mount(TabContainer, {
      props: {
        tabs: mockTabs
      },
      slots: {
        tab1: '<div class="slot-content">{{ tab.label }} - Active: {{ active }}</div>'
      }
    })

    // The slot should receive tab and active props
    expect(wrapperWithProps.find('.slot-content').exists()).toBe(true)
  })

  it('filters out invalid tabs', () => {
    const invalidTabs = [
      { id: '', label: 'Invalid Tab' }, // Invalid: empty id
      { id: 'valid', label: '' }, // Invalid: empty label
      { id: 'valid-tab', label: 'Valid Tab' } // Valid
    ]

    const filterWrapper = mount(TabContainer, {
      props: {
        tabs: invalidTabs
      },
      slots: {
        'valid-tab': '<div>Valid content</div>'
      }
    })

    expect(filterWrapper.vm.getTabs()).toHaveLength(1)
    expect(filterWrapper.vm.getTabs()[0].id).toBe('valid-tab')
  })

  it('exposes correct methods', () => {
    expect(typeof wrapper.vm.setActiveTab).toBe('function')
    expect(typeof wrapper.vm.getActiveTab).toBe('function')
    expect(typeof wrapper.vm.getTabs).toBe('function')
  })

  it('programmatically sets active tab', () => {
    wrapper.vm.setActiveTab('tab2')
    expect(wrapper.vm.getActiveTab()).toBe('tab2')
    
    // Should not set disabled tab
    wrapper.vm.setActiveTab('tab3')
    expect(wrapper.vm.getActiveTab()).toBe('tab2') // Still tab2
  })
})

describe('TabNavigation.vue', () => {
  const mockTabs = [
    { id: 'tab1', label: 'Tab 1', icon: 'home' },
    { id: 'tab2', label: 'Tab 2' },
    { id: 'tab3', label: 'Tab 3', disabled: true }
  ]

  let wrapper

  beforeEach(() => {
    wrapper = mount(TabNavigation, {
      props: {
        tabs: mockTabs,
        activeTab: 'tab1'
      }
    })
  })

  it('renders all tabs', () => {
    const buttons = wrapper.findAll('.tab-button')
    expect(buttons).toHaveLength(3)
  })

  it('marks active tab correctly', () => {
    const buttons = wrapper.findAll('.tab-button')
    expect(buttons[0].classes()).toContain('active')
    expect(buttons[1].classes()).not.toContain('active')
  })

  it('marks disabled tab correctly', () => {
    const buttons = wrapper.findAll('.tab-button')
    expect(buttons[2].classes()).toContain('disabled')
    expect(buttons[2].attributes('disabled')).toBeDefined()
  })

  it('shows icons when provided', () => {
    const firstButton = wrapper.findAll('.tab-button')[0]
    expect(firstButton.find('.fa-icon').exists()).toBe(true)
    
    const secondButton = wrapper.findAll('.tab-button')[1]
    expect(secondButton.find('.fa-icon').exists()).toBe(false)
  })

  it('emits tab-change when non-disabled tab is clicked', async () => {
    const buttons = wrapper.findAll('.tab-button')
    
    await buttons[1].trigger('click')
    
    expect(wrapper.emitted('tab-change')).toBeTruthy()
    expect(wrapper.emitted('tab-change')[0]).toEqual(['tab2'])
  })

  it('does not emit tab-change when disabled tab is clicked', async () => {
    const buttons = wrapper.findAll('.tab-button')
    
    await buttons[2].trigger('click')
    
    expect(wrapper.emitted('tab-change')).toBeFalsy()
  })

  it('does not emit tab-change when active tab is clicked', async () => {
    const buttons = wrapper.findAll('.tab-button')
    
    await buttons[0].trigger('click') // Click active tab
    
    expect(wrapper.emitted('tab-change')).toBeFalsy()
  })

  it('applies custom navigation class', () => {
    const customWrapper = mount(TabNavigation, {
      props: {
        tabs: mockTabs,
        activeTab: 'tab1',
        navigationClass: 'custom-nav'
      }
    })

    expect(customWrapper.find('.tab-navigation').classes()).toContain('custom-nav')
  })
})

describe('TabPanel.vue', () => {
  it('renders when active', () => {
    const wrapper = mount(TabPanel, {
      props: {
        tabId: 'test-tab',
        active: true
      },
      slots: {
        default: '<div class="panel-content">Panel Content</div>'
      }
    })

    expect(wrapper.find('.tab-panel').exists()).toBe(true)
    expect(wrapper.find('.panel-content').exists()).toBe(true)
    expect(wrapper.find('.tab-panel').isVisible()).toBe(true)
  })

  it('is hidden when not active', () => {
    const wrapper = mount(TabPanel, {
      props: {
        tabId: 'test-tab',
        active: false
      },
      slots: {
        default: '<div class="panel-content">Panel Content</div>'
      }
    })

    expect(wrapper.find('.tab-panel').exists()).toBe(true)
    expect(wrapper.find('.tab-panel').isVisible()).toBe(false)
  })

  it('applies custom panel class', () => {
    const wrapper = mount(TabPanel, {
      props: {
        tabId: 'test-tab',
        active: true,
        panelClass: 'custom-panel'
      },
      slots: {
        default: '<div>Content</div>'
      }
    })

    expect(wrapper.find('.tab-panel').classes()).toContain('custom-panel')
  })

  it('has correct accessibility attributes', () => {
    const wrapper = mount(TabPanel, {
      props: {
        tabId: 'test-tab',
        active: true
      },
      slots: {
        default: '<div>Content</div>'
      }
    })

    const panel = wrapper.find('.tab-panel')
    expect(panel.attributes('id')).toBe('tabpanel-test-tab')
    expect(panel.attributes('role')).toBe('tabpanel')
    expect(panel.attributes('aria-labelledby')).toBe('tab-test-tab')
  })
})