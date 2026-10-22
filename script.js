const API = '/api/songs';
let songs = [];
let selectedId = null;
let playing = false;

const rows = document.getElementById('songRows');
const emptyState = document.getElementById('emptyState');
const searchInput = document.getElementById('searchInput');
const formMessage = document.getElementById('formMessage');

async function loadSongs() {
  try {
    const response = await fetch(API);
    if (!response.ok) throw new Error('Could not load playlist');
    songs = await response.json();
    renderSongs();
    updatePlayer();
  } catch (error) {
    formMessage.textContent = 'Could not connect to Java server. Start it using the README instructions.';
    formMessage.style.color = '#ff9292';
  }
}

function renderSongs() {
  const query = searchInput.value.trim().toLowerCase();
  const filtered = songs.filter(song =>
    song.title.toLowerCase().includes(query) || song.artist.toLowerCase().includes(query)
  );
  rows.innerHTML = '';
  filtered.forEach((song, index) => {
    const tr = document.createElement('tr');
    const coverClass = ['','alt','warm'][index % 3];
    tr.innerHTML = `
      <td>${index + 1}</td>
      <td><div class="track-cell"><div class="track-cover ${coverClass}">♫</div><span></span></div></td>
      <td></td>
      <td><div class="row-actions"><button class="small-action play-song" data-id="${song.id}">▶ Play</button><button class="small-action delete" data-id="${song.id}">Remove</button></div></td>`;
    tr.querySelector('.track-cell span').textContent = song.title;
    tr.children[2].textContent = song.artist;
    rows.appendChild(tr);
  });
  emptyState.style.display = filtered.length ? 'none' : 'block';
  document.getElementById('trackCount').textContent = songs.length;
  document.getElementById('countPill').textContent = `${songs.length} ${songs.length === 1 ? 'track' : 'tracks'}`;
}

document.getElementById('addForm').addEventListener('submit', async event => {
  event.preventDefault();
  const title = document.getElementById('songTitle').value.trim();
  const artist = document.getElementById('songArtist').value.trim();
  if (!title || !artist) return;
  try {
    const response = await fetch(API, {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({title, artist})
    });
    const result = await response.json();
    if (!response.ok) throw new Error(result.error || 'Could not add song');
    songs.push(result);
    selectedId = result.id;
    playing = false;
    document.getElementById('addForm').reset();
    formMessage.style.color = 'var(--lime)';
    formMessage.textContent = 'Track added to your playlist.';
    renderSongs();
    updatePlayer();
  } catch (error) {
    formMessage.style.color = '#ff9292';
    formMessage.textContent = error.message;
  }
});

rows.addEventListener('click', async event => {
  const button = event.target.closest('button');
  if (!button) return;
  const id = Number(button.dataset.id);
  if (button.classList.contains('play-song')) {
    selectedId = id;
    playing = true;
    updatePlayer();
  } else if (button.classList.contains('delete')) {
    try {
      const response = await fetch(`${API}?id=${id}`, {method: 'DELETE'});
      if (!response.ok) throw new Error('Could not remove song');
      songs = songs.filter(song => song.id !== id);
      if (selectedId === id) { selectedId = songs[0]?.id ?? null; playing = false; }
      renderSongs();
      updatePlayer();
      formMessage.style.color = 'var(--lime)';
      formMessage.textContent = 'Track removed from your playlist.';
    } catch (error) {
      formMessage.style.color = '#ff9292';
      formMessage.textContent = error.message;
    }
  }
});

function updatePlayer() {
  const song = songs.find(item => item.id === selectedId);
  document.getElementById('playerTitle').textContent = song ? song.title : 'Choose a song';
  document.getElementById('playerArtist').textContent = song ? song.artist : 'Your playlist starts here';
  document.getElementById('nowPlayingStat').textContent = song ? song.title : 'Nothing yet';
  document.getElementById('playBtn').textContent = playing ? 'Ⅱ' : '▶';
  document.getElementById('playerStatus').textContent = song ? (playing ? 'Playing (demo)' : 'Selected track') : 'Ready when you are';
}

function moveSong(direction) {
  if (!songs.length) return;
  let index = songs.findIndex(song => song.id === selectedId);
  if (index < 0) index = direction > 0 ? -1 : 0;
  index = (index + direction + songs.length) % songs.length;
  selectedId = songs[index].id;
  playing = true;
  updatePlayer();
}

document.getElementById('prevBtn').addEventListener('click', () => moveSong(-1));
document.getElementById('nextBtn').addEventListener('click', () => moveSong(1));
document.getElementById('playBtn').addEventListener('click', () => {
  if (!songs.length) return;
  if (!selectedId) selectedId = songs[0].id;
  playing = !playing;
  updatePlayer();
});
searchInput.addEventListener('input', renderSongs);
document.getElementById('refreshBtn').addEventListener('click', loadSongs);
loadSongs();
