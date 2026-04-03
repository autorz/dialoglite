with open('app/templates/index.html', 'r') as f:
    content = f.read()

js_script = """
    document.addEventListener("DOMContentLoaded", function() {
        // Catppuccin Colors
        const ctpText = {r: 205, g: 214, b: 244};   // #cdd6f4
        const ctpGreen = {r: 166, g: 227, b: 161};  // #a6e3a1
        const ctpRed = {r: 243, g: 139, b: 168};    // #f38ba8

        function interpolateColor(color1, color2, factor) {
            if (factor > 1) factor = 1;
            if (factor < 0) factor = 0;
            const r = Math.round(color1.r + factor * (color2.r - color1.r));
            const g = Math.round(color1.g + factor * (color2.g - color1.g));
            const b = Math.round(color1.b + factor * (color2.b - color1.b));
            return `rgb(${r}, ${g}, ${b})`;
        }

        // We calculate max and min over the last 90 days.
        // History array is sorted descending, newest first.
        const today = new Date();
        const ninetyDaysAgo = new Date();
        ninetyDaysAgo.setDate(today.getDate() - 90);

        let maxDelta = 0, minDelta = 0;
        let maxBalance = 0, minBalance = 0;

        const deltaCells = document.querySelectorAll('.daily-delta-cell');
        const balanceCells = document.querySelectorAll('.accumulated-balance-cell');

        function parseDateString(dateStr) {
            const parts = dateStr.split('-');
            return new Date(parts[0], parts[1] - 1, parts[2]);
        }

        // Find min/max for Delta
        deltaCells.forEach(cell => {
            const cellDate = parseDateString(cell.getAttribute('data-date'));
            if (cellDate >= ninetyDaysAgo && cellDate <= today) {
                const val = parseFloat(cell.getAttribute('data-value'));
                if (!isNaN(val)) {
                    if (val > maxDelta) maxDelta = val;
                    if (val < minDelta) minDelta = val;
                }
            }
        });

        // Find min/max for Balance
        balanceCells.forEach(cell => {
            const cellDate = parseDateString(cell.getAttribute('data-date'));
            if (cellDate >= ninetyDaysAgo && cellDate <= today) {
                const val = parseFloat(cell.getAttribute('data-value'));
                if (!isNaN(val)) {
                    if (val > maxBalance) maxBalance = val;
                    if (val < minBalance) minBalance = val;
                }
            }
        });

        // Apply colors for Delta
        deltaCells.forEach(cell => {
            const val = parseFloat(cell.getAttribute('data-value'));
            if (!isNaN(val)) {
                if (val > 0 && maxDelta > 0) {
                    const factor = val / maxDelta;
                    cell.style.color = interpolateColor(ctpText, ctpGreen, factor);
                } else if (val < 0 && minDelta < 0) {
                    const factor = val / minDelta; // both negative, so ratio is positive
                    cell.style.color = interpolateColor(ctpText, ctpRed, factor);
                } else {
                    cell.style.color = `rgb(${ctpText.r}, ${ctpText.g}, ${ctpText.b})`;
                }
                cell.style.setProperty("color", cell.style.color, "important");
            }
        });

        // Apply colors for Balance
        balanceCells.forEach(cell => {
            const val = parseFloat(cell.getAttribute('data-value'));
            if (!isNaN(val)) {
                if (val > 0 && maxBalance > 0) {
                    const factor = val / maxBalance;
                    cell.style.color = interpolateColor(ctpText, ctpGreen, factor);
                } else if (val < 0 && minBalance < 0) {
                    const factor = val / minBalance;
                    cell.style.color = interpolateColor(ctpText, ctpRed, factor);
                } else {
                    cell.style.color = `rgb(${ctpText.r}, ${ctpText.g}, ${ctpText.b})`;
                }
                cell.style.setProperty("color", cell.style.color, "important");

                // If it contains a badge with style inherited, let it be. But we can explicitly color the inner span just in case.
                const innerSpan = cell.querySelector('span.badge');
                if (innerSpan) {
                     innerSpan.style.setProperty("color", "#11111b", "important"); // Keep badge text dark, background handles it
                     innerSpan.style.setProperty("background-color", cell.style.color, "important");
                     cell.style.color = ''; // clear parent text color so badge bg stands out
                }
            }
        });
        """

replacement = """    document.addEventListener("DOMContentLoaded", function() {""" + js_script + """
        var ctx = document.getElementById('balanceChart').getContext('2d');"""

content = content.replace("    document.addEventListener(\"DOMContentLoaded\", function() {\n        var ctx = document.getElementById('balanceChart').getContext('2d');", replacement)

with open('app/templates/index.html', 'w') as f:
    f.write(content)
