// User-card component — renders a user ({username, email, role}) as a card.
// User data is filled via textContent (never innerHTML) so attacker-controlled
// strings can't inject markup.
// Usage: import { createUserCard } from '/js/components/user-card/user-card.js';
//        const card = await createUserCard({ username, email, role });
//        container.appendChild(card);

let templateHtml = null;

export async function createUserCard({ username, email, role }) {
    loadStylesOnce('/js/components/user-card/user-card.css', 'user-card');

    if (!templateHtml) {
        const res = await fetch('/js/components/user-card/user-card.html');
        templateHtml = await res.text();
    }

    const wrapper = document.createElement('div');
    wrapper.innerHTML = templateHtml.trim();
    const card = wrapper.firstElementChild;

    card.querySelector('[data-field="initials"]').textContent =
        (username || '?').substring(0, 2).toUpperCase();
    card.querySelector('[data-field="username"]').textContent = username || '';
    card.querySelector('[data-field="email"]').textContent = email || '';
    card.querySelector('[data-field="role"]').textContent = role || 'USER';

    return card;
}

function loadStylesOnce(href, componentName) {
    if (document.querySelector(`link[data-component="${componentName}"]`)) return;
    const link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = href;
    link.dataset.component = componentName;
    document.head.appendChild(link);
}
