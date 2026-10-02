// The source of the Navigation preference tiles (Settings > Appearance and themes > Navigation preference).
// To add a preference, add one entry here: the tile appears in the dialog by itself, and picking it saves its id on the account.
//   { id: 'bottom-pill', name: 'Bottom pill', note: 'Nav along the bottom' }
//     id     unique and stable (it is what gets saved)
//     name   the big line on the tile, also the line under "Navigation preference" in Settings
//     note   optional small line under the name   (tiles are ash; the picked one turns blue and glows, in css/pages/nav-preference.css)
//     image  optional { dusk, light }: a transparent picture per theme, shown on the tile (it switches when the theme does)
export const NAV_PREFERENCES = [
    { id: 'return', name: 'Return buttons', note: 'A ‹ in every header, no wheel', image: { dusk: '/icons/nav-return-dusk.svg', light: '/icons/nav-return-light.svg' } },
    { id: 'omni-wheel', name: 'Omni wheel', note: 'The wheel at the bottom', image: { dusk: '/icons/nav-wheel-dusk.svg', light: '/icons/nav-wheel-light.svg' } },
];

// The id used until the account picks one.
export const DEFAULT_NAV_PREFERENCE = 'return';
