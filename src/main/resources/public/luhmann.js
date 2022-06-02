var socket = new WebSocket(location.origin.replace(/^http/, 'ws') + '/ws');
socket.addEventListener('message', function (event) { window.location.reload(); });

function currentNote() {
    var loc = location.pathname;
    if (loc === '/') {
        var note = 'index.adoc';
    } else {
        var note = loc.replace(/^\//, '').replace(/html$/, 'adoc');
    }
    return note;
}

function focusSearch () {
    var input = document.getElementsByName('q').item(0)
    input.focus();
    input.select();
}

function hideToast() {
    var toast = document.getElementById('luh-toast');
    if (toast) {
        toast.remove();
    }
}

function showToast(text) {
    hideToast();
    var toast = document.createElement('div');
    toast.id = 'luh-toast';
    var inner = document.createElement('div');
    inner.className = 'luh-toast-inner';
    inner.appendChild(document.createTextNode(text));
    toast.appendChild(inner);
    document.body.appendChild(toast);
    setTimeout(hideToast, 1000);
}

function keyListener (e) {

    if (document.activeElement && e.key === 'Escape') {

        document.activeElement.blur();

    } else if (e.target.tagName === 'BODY') {

        if (e.key === '/') {
            e.preventDefault();
            focusSearch();

        } else if (e.key === 'e') {
            showToast('Launching editor...');
            var xhr = new XMLHttpRequest();
            //xhr.addEventListener('load', reqListener);
            xhr.open('GET', '/api/edit?note=' + currentNote());
            xhr.send();

        } else if (e.key === 'y') {
            var el = document.getElementById('luh-copy-input')
            el.value = 'xref:' + currentNote() + '[' + document.title + ']';
            el.select();
            el.setSelectionRange(0, 9999);
            document.execCommand('copy');
            el.blur();
            showToast('Xref copied to clipboard');
        }
    }
}

document.addEventListener('keydown', keyListener)
