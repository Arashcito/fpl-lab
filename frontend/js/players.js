async function loadPlayers(sortBy = "totalPoints", order = "desc") {
    const res = await fetch(`http://localhost:8080/api/players?sortBy=${sortBy}&order=${order}`);
    const players = await res.json();

    const body = document.getElementById("playersBody");
    body.innerHTML = "";

    players.forEach(p => {
        const row = document.createElement("tr");
        row.innerHTML = `
      <td>${p.webName}</td>
      <td>${p.teamName ?? p.teamId}</td>
      <td>${p.positionName ?? p.positionId}</td>
      <td>${p.price}</td>
      <td>${p.totalPoints}</td>
    `;
        body.appendChild(row);
    });
}

document.getElementById("apply").addEventListener("click", () => {
    const sortBy = document.getElementById("sortBy").value;
    const order = document.getElementById("order").value;
    loadPlayers(sortBy, order);
});

loadPlayers();
