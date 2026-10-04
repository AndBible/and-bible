// Click-to-load YouTube: nothing is fetched from YouTube until the reader
// presses play. Without JavaScript the thumbnail stays and the
// "Watch on YouTube" link still works. With JavaScript the play button is enough, so the
// js-yt class lets embeds.css hide that link on listing cards (blog embeds keep it).
document.documentElement.classList.add("js-yt");
document.addEventListener("click", (event) => {
  const button = event.target.closest(".yt__play");
  if (!button) return;
  const box = button.closest(".yt");
  const id = box.dataset.ytId;
  const frame = document.createElement("iframe");
  frame.src = `https://www.youtube-nocookie.com/embed/${id}?autoplay=1&rel=0`;
  frame.title = button.getAttribute("aria-label") || "YouTube video";
  frame.allow = "autoplay; encrypted-media; picture-in-picture; fullscreen";
  frame.allowFullscreen = true;
  frame.className = "yt__frame";
  button.replaceWith(frame);
});
