const { app, BrowserWindow, ipcMain } = require('electron');
const path = require('path');
const dgram = require('dgram');

const SERVER_PORT = 3456;
const SERVER_IP = 'localhost';
const udpClient = dgram.createSocket('udp4');

let mainWindow;

function createWindow() {
    mainWindow = new BrowserWindow({
        width: 1024,
        height: 768,
        webPreferences: {
            preload: path.join(__dirname, 'preload.js'),
            contextIsolation: true,
            nodeIntegration: false
        }
    });

    mainWindow.loadFile('index.html');
    // mainWindow.webContents.openDevTools(); // Bỏ comment dòng này nếu muốn mở F12 để debug UI
}

app.whenReady().then(createWindow);

app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') app.quit();
    udpClient.close();
});

// Lắng nghe dữ liệu phản hồi từ Java Server qua UDP
udpClient.on('message', (msg) => {
    const response = msg.toString('utf-8');
    if (mainWindow) {
        // Gửi dữ liệu nhận được lên giao diện (renderer)
        mainWindow.webContents.send('udp-response', response);
    }
});

// Nhận lệnh từ giao diện (UI) và gửi xuống Java Server
ipcMain.on('send-udp', (event, command) => {
    const message = Buffer.from(command, 'utf-8');
    udpClient.send(message, 0, message.length, SERVER_PORT, SERVER_IP, (err) => {
        if (err) console.error("Lỗi gửi UDP:", err);
    });
});