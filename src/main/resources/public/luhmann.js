var socket = new WebSocket(location.origin.replace(/^http/, 'ws') + '/ws');
socket.addEventListener('message', function (event) { window.location.reload(); });

function focusSearch () {
    var input = document.getElementsByName('q').item(0)
    input.focus();
    input.select();
}

function searchKeyListener (e) {
    if (e.key === '\\') {
        e.preventDefault();
        focusSearch();
    }
}

document.addEventListener('keydown', searchKeyListener)
