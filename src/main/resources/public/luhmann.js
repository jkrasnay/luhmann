var socket = new WebSocket(location.origin.replace(/^http/, 'ws') + '/ws');
socket.addEventListener('message', function (event) { window.location.reload(); });

