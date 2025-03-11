console.log("highlight.js загружен на", window.location.href);

document.addEventListener("DOMContentLoaded", function () {
    let query = new URLSearchParams(window.location.search).get("query");

    if (!query) {
        query = sessionStorage.getItem("query"); // Если query нет в URL, берём из sessionStorage
    }

    console.log("🔍 Искомое слово:", query);

    if (query) {
        highlightAndScrollTo(query);
    } else {
        console.warn("❌ Query не найдено!");
    }
});

function highlightAndScrollTo(query) {
    if (!query) return;

    console.log("🟡 Начинаем подсветку и прокрутку...");

    const escapedQuery = query.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    const regex = new RegExp(`(${escapedQuery}[а-яa-z]*)`, "gi");

    let firstHighlight = null;

    document.querySelectorAll("p, span, div, a, li").forEach(element => {
        if (element.children.length === 0 && regex.test(element.textContent)) {
            element.innerHTML = element.innerHTML.replace(regex, `<span class="highlight">$1</span>`);

            if (!firstHighlight) {
                firstHighlight = element.querySelector('.highlight');
            }
        }
    });

    if (firstHighlight) {
        setTimeout(() => {
            console.log("✅ Прокрутка к:", firstHighlight.textContent);
            firstHighlight.scrollIntoView({ behavior: "smooth", block: "center" });
        }, 500);
    } else {
        console.warn("❌ Совпадения не найдены!");
    }
}
