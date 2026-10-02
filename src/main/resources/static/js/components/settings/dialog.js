// A sub-modal of the Settings page: a title and an empty body to fill. onClose runs once when it closes (the row behind it refreshes its line).
import { openGlassBlurDialog } from '/js/components/glass-blur-dialog/glass-blur-dialog.js';

export async function settingsDialog(title, onClose) {
    const { panel } = await openGlassBlurDialog({ size: 'sm', label: title, onClose, html: `<h3 class="glass-blur-dialog__title">${title}</h3><div class="pst-body"></div>` });   // titles are fixed strings
    return panel.lastElementChild;
}
