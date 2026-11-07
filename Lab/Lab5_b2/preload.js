const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('mailAPI', {
    sendCommand: (command) => ipcRenderer.send('send-udp', command),
    onResponse: (callback) => ipcRenderer.on('udp-response', (event, response) => callback(response))
});