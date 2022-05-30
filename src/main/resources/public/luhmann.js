var socket = new WebSocket(location.origin.replace(/^http/, 'ws') + '/ws');
socket.addEventListener('message', function (event) { window.location.reload(); });

function focusSearch () {
    var input = document.getElementsByName('q').item(0)
    input.focus();
    input.select();
}

function keyListener (e) {
    if (e.target.tagName === 'BODY') {
        if (e.key === '/') {
            e.preventDefault();
            focusSearch();
        } else if (e.key === 'e') {
            var loc = location.pathname;
            if (loc === '/') {
                var note = 'index.adoc';
            } else {
                var note = loc.replace(/^\//, '').replace(/html$/, 'adoc');
            }
            var xhr = new XMLHttpRequest();
            //xhr.addEventListener('load', reqListener);
            xhr.open('GET', '/api/edit?note=' + note);
            xhr.send();
        }
    }
}

document.addEventListener('keydown', keyListener)
