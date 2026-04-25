export const BRAND_THEMES = [
    { id: 'default', name: 'Default (Blue)', asset: '/assets/themes/theme-default.png', primaryColor: '#007bff' },
    { id: 'burgundy', name: 'Burgundy & Gold (Super Admin)', asset: '/assets/themes/theme-burgundy.png', primaryColor: '#800020' },
    { id: 'emerald', name: 'Emerald Green', asset: '/assets/themes/theme-emerald.png', primaryColor: '#50c878' },
    { id: 'purple', name: 'Royal Purple', asset: '/assets/themes/theme-purple.png', primaryColor: '#7851a9' },
    { id: 'cyan', name: 'Cyan & Teal', asset: '/assets/themes/theme-cyan.png', primaryColor: '#008b8b' },
    { id: 'crimson', name: 'Crimson Red', asset: '/assets/themes/theme-crimson.png', primaryColor: '#dc143c' },
    { id: 'navy', name: 'Classic Navy', asset: '/assets/themes/theme-navy.png', primaryColor: '#000080' },
    { id: 'black', name: 'Black & Graphite', asset: '/assets/themes/theme-black.png', primaryColor: '#333333' },
    { id: 'orange', name: 'Orange & Grey', asset: '/assets/themes/theme-orange.png', primaryColor: '#ff7f50' },
    { id: 'lime', name: 'Neon Lime', asset: '/assets/themes/theme-lime.png', primaryColor: '#32cd32' },
    { id: 'bronze', name: 'Bronze & Brown', asset: '/assets/themes/theme-bronze.png', primaryColor: '#cd7f32' },
    { id: 'ocean', name: 'Deep Ocean', asset: '/assets/themes/theme-ocean.png', primaryColor: '#006994' },
    { id: 'lavender', name: 'Lavender & Indigo', asset: '/assets/themes/theme-lavender.png', primaryColor: '#e6e6fa' }
];

export type ThemeId = typeof BRAND_THEMES[number]['id'];
