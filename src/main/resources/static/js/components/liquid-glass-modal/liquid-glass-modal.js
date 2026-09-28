// Liquid Glass Modal — faithful vanilla port of the Lovable React component.
export async function mountLiquidGlassModal(targetSelector = '#liquid-glass-modal', options = {}) {
    const target = document.querySelector(targetSelector);
    if (!target) return;

    loadStylesOnce('/js/components/liquid-glass-modal/liquid-glass-modal.css', 'liquid-glass-modal');

    const res = await fetch('/js/components/liquid-glass-modal/liquid-glass-modal.html');
    const html = await res.text();

    const wrapper = document.createElement('div');
    wrapper.innerHTML = html;
    while (wrapper.firstChild) {
        target.appendChild(wrapper.firstChild);
    }

    const portal = target.querySelector('.liquid-glass-portal');
    const backdrop = target.querySelector('.glass-backdrop');
    const closeBtn = target.querySelector('.liquid-glass__close');
    const modal = target.querySelector('.liquid-glass');

    if (!portal || !modal) return;

    // Lock body scroll and focus the close button.
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    closeBtn?.focus();

    function close() {
        portal.remove();
        target.querySelector('.liquid-glass__filter')?.remove();
        document.body.style.overflow = previousOverflow;
        if (typeof options.onClose === 'function') options.onClose();
        else if (options.trigger) options.trigger.focus();
    }

    backdrop?.addEventListener('click', close);
    closeBtn?.addEventListener('click', close);

    function handleKeyDown(event) {
        if (event.key === 'Escape') {
            close();
        }
        if (event.key === 'Tab') {
            trapFocus(event, modal);
        }
    }

    window.addEventListener('keydown', handleKeyDown);

    // Clean up the global keydown listener if the portal is removed externally.
    const observer = new MutationObserver(() => {
        if (!document.contains(portal)) {
            window.removeEventListener('keydown', handleKeyDown);
            observer.disconnect();
        }
    });
    observer.observe(document.body, { childList: true, subtree: true });

    if (typeof options.onMount === 'function') options.onMount(modal);
}

function loadStylesOnce(href, componentName) {
    if (document.querySelector(`link[data-component="${componentName}"]`)) return;
    const link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = href;
    link.dataset.component = componentName;
    document.head.appendChild(link);
}

function trapFocus(event, container) {
    const focusable = container.querySelectorAll(
        'a[href], button:not([disabled]), textarea, input, select, [tabindex]:not([tabindex="-1"])'
    );
    if (focusable.length === 0) return;
    const first = focusable[0];
    const last = focusable[focusable.length - 1];

    if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
    }
}
