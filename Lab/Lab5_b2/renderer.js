let currentUser = "";
let currentPass = "";
let readBuffers = {};

const authScreen = document.getElementById('auth-screen');
const authEmail = document.getElementById('auth-email');
const authPassword = document.getElementById('auth-password');
const authError = document.getElementById('auth-error');
const currentUserDisplay = document.getElementById('current-user-display');
const mailList = document.getElementById('mail-list');
const viewMailPanel = document.getElementById('view-mail-panel');
const composePanel = document.getElementById('compose-panel');
const emptyPanel = document.getElementById('empty-panel');
const mailContent = document.getElementById('mail-content');
const mailMedia = document.getElementById('mail-media');
const toast = document.getElementById('toast');

function isValidEmail(email) {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
}

function showToast(message, isError = false) {
    toast.textContent = message;
    toast.className = `absolute top-4 right-4 px-4 py-2 rounded shadow-lg text-white font-medium transition-opacity duration-300 z-50 ${isError ? 'bg-red-500' : 'bg-gray-800'}`;
    toast.style.opacity = '1';
    setTimeout(() => { toast.style.opacity = '0'; }, 4000);
}

function switchPanel(panelId) {
    viewMailPanel.classList.add('hidden');
    composePanel.classList.add('hidden');
    emptyPanel.classList.add('hidden');
    document.getElementById(panelId).classList.remove('hidden');
}

// Chuyển đổi định dạng CSS của Navbar
function updateNavStyles(activeTab) {
    const navInbox = document.getElementById('nav-inbox');
    const navSent = document.getElementById('nav-sent');

    if (activeTab === 'inbox') {
        navInbox.className = "flex items-center gap-3 p-3 bg-[#d3e3fd] text-[#041e49] rounded-r-full font-bold";
        navSent.className = "flex items-center gap-3 p-3 text-gray-700 hover:bg-gray-100 rounded-r-full font-medium transition-colors";
    } else {
        navSent.className = "flex items-center gap-3 p-3 bg-[#d3e3fd] text-[#041e49] rounded-r-full font-bold";
        navInbox.className = "flex items-center gap-3 p-3 text-gray-700 hover:bg-gray-100 rounded-r-full font-medium transition-colors";
    }
}

window.mailAPI.onResponse((response) => {
    if (response === "IGNORE") return;

    const firstPipeIndex = response.indexOf('|');
    if (firstPipeIndex === -1) return;

    const command = response.substring(0, firstPipeIndex);
    const data = response.substring(firstPipeIndex + 1);

    if (command === "CONTENT_CHUNK") {
        const parts = data.split('|');
        const fName = parts[0];
        const total = parseInt(parts[1]);
        const idx = parseInt(parts[2]);
        const chunkData = parts.slice(3).join('|');

        if (!readBuffers[fName]) readBuffers[fName] = new Array(total);
        readBuffers[fName][idx] = chunkData;

        if (readBuffers[fName].filter(x => x !== undefined).length === total) {
            const fullContent = readBuffers[fName].join('');
            delete readBuffers[fName];
            displayEmailContent(fullContent);
        }
        return;
    }

    if (command === "LIST") {
        if (!authScreen.classList.contains('hidden')) {
            currentUser = authEmail.value.trim();
            currentPass = authPassword.value.trim();
            currentUserDisplay.textContent = currentUser;
            authScreen.classList.add('hidden');
            switchPanel('empty-panel');
        }
        renderMailList(data, "inbox");
    }
    else if (command === "LIST_SENT") {
        renderMailList(data, "sent");
    }
    else if (command === "SUCCESS_CREATE") {
        if (!authScreen.classList.contains('hidden')) {
            showToast(data, false);
            authPassword.value = "";
        }
    }
    else if (command === "SUCCESS" || command === "ERROR") {
        const isError = command === "ERROR";

        if (!authScreen.classList.contains('hidden')) {
            if (isError) {
                authError.textContent = data;
                authError.classList.remove('hidden');
            }
        } else {
            showToast(data, isError);
            if (command === "SUCCESS" && !composePanel.classList.contains('hidden')) {
                document.getElementById('compose-receiver').value = '';
                document.getElementById('compose-subject').value = '';
                document.getElementById('compose-content').value = '';
                document.getElementById('compose-file').value = '';
                refreshInbox();
                switchPanel('empty-panel');
            }
        }
    }
});

function displayEmailContent(fullContent) {
    mailMedia.innerHTML = '';
    const attachSplit = fullContent.split('\n\n---ATTACHMENT---\n');
    const textPart = attachSplit[0];
    const base64Part = attachSplit[1] ? attachSplit[1].trim() : null;

    mailContent.textContent = textPart;

    if (base64Part) {
        let mimeType = "image/png";
        if (textPart.toLowerCase().match(/\.jpe?g/)) mimeType = "image/jpeg";
        else if (textPart.toLowerCase().includes('.gif')) mimeType = "image/gif";

        const img = document.createElement('img');
        img.src = `data:${mimeType};base64,${base64Part}`;
        img.className = "max-w-full h-auto rounded border border-gray-200 shadow-sm max-h-96 object-contain";
        mailMedia.appendChild(img);
    }

    switchPanel('view-mail-panel');
}

function handleAuth(action) {
    const email = authEmail.value.trim();
    const password = authPassword.value.trim();

    if (!isValidEmail(email)) {
        authError.textContent = "Email không hợp lệ!";
        authError.classList.remove('hidden');
        return;
    }
    if (!password) {
        authError.textContent = "Vui lòng nhập mật khẩu!";
        authError.classList.remove('hidden');
        return;
    }
    if (password.length < 6) {
        authError.textContent = "Mật khẩu phải có ít nhất 6 ký tự!";
        authError.classList.remove('hidden');
        return;
    }

    authError.classList.add('hidden');
    window.mailAPI.sendCommand(`${action}|${email}|${password}`);
}

function logout() {
    currentUser = "";
    currentPass = "";
    authScreen.classList.remove('hidden');
    authEmail.value = "";
    authPassword.value = "";
    mailList.innerHTML = "";
    switchPanel('empty-panel');
}

function refreshInbox() {
    if (currentUser && currentPass) {
        window.mailAPI.sendCommand(`LOGIN|${currentUser}|${currentPass}`);
        updateNavStyles('inbox');
    }
}

function refreshSent() {
    if (currentUser) {
        window.mailAPI.sendCommand(`LIST_SENT|${currentUser}`);
        updateNavStyles('sent');
    }
}

function showCompose() {
    switchPanel('compose-panel');
}

function sendMail() {
    const receiver = document.getElementById('compose-receiver').value.trim();
    const subject = document.getElementById('compose-subject').value.trim();
    const content = document.getElementById('compose-content').value.trim();
    const fileInput = document.getElementById('compose-file');

    if (!isValidEmail(receiver)) {
        showToast("Email người nhận không hợp lệ!", true);
        return;
    }
    if (!content && fileInput.files.length === 0) {
        showToast("Thư phải có nội dung hoặc file đính kèm!", true);
        return;
    }

    const finalSubject = subject ? subject : "(Không có chủ đề)";

    if (fileInput.files.length > 0) {
        sendFileUDP(fileInput.files[0], receiver, finalSubject, content);
    } else {
        window.mailAPI.sendCommand(`SEND|${currentUser}|${receiver}|${finalSubject}|${content}`);
    }
}

function sendFileUDP(file, receiver, subject, content) {
    const reader = new FileReader();
    reader.onload = function (e) {
        const base64Data = e.target.result.split(',')[1];
        const fileName = file.name.replace(/\|/g, '');

        const payload = `${subject}|||${content}|||${fileName}|||${base64Data}`;
        const CHUNK_SIZE = 4000;
        const totalChunks = Math.ceil(payload.length / CHUNK_SIZE);

        showToast(`Đang gửi đính kèm: ${fileName} (${totalChunks} gói tin)...`);

        let chunkIndex = 0;
        const interval = setInterval(() => {
            if (chunkIndex >= totalChunks) {
                clearInterval(interval);
                return;
            }
            const chunk = payload.substring(chunkIndex * CHUNK_SIZE, (chunkIndex + 1) * CHUNK_SIZE);
            window.mailAPI.sendCommand(`FILE_CHUNK|${currentUser}|${receiver}|${totalChunks}|${chunkIndex}|${chunk}`);
            chunkIndex++;
        }, 20);
    };
    reader.readAsDataURL(file);
}

function readMail(fileName) {
    window.mailAPI.sendCommand(`READ|${currentUser}|${fileName}`);
}

// Cập nhật hàm Render để hiển thị cho cả Thư đến và Thư đã gửi
function renderMailList(filesString, type) {
    mailList.innerHTML = "";
    if (!filesString.trim()) {
        mailList.innerHTML = `<div class="p-6 text-center text-gray-400 text-sm">Hộp thư trống</div>`;
        return;
    }

    const files = filesString.split(",");
    const prefix = type === "sent" ? "sent_" : "email_";
    const validFiles = files.filter(f => f.startsWith(prefix));

    validFiles.reverse().forEach(file => {
        const item = document.createElement('div');
        item.className = "p-4 border-b border-gray-100 hover:bg-gray-50 cursor-pointer bg-white transition-all group";

        const timeStr = file.replace(prefix, '').replace('.txt', '');
        let timeDisplay = "N/A";
        if (!isNaN(timeStr)) {
            const date = new Date(parseInt(timeStr));
            timeDisplay = date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        }

        const title = type === "sent" ? "📤 Thư đã gửi" : "✉️ Thư mới";

        item.innerHTML = `
            <div class="flex justify-between items-baseline mb-1">
                <h4 class="font-bold text-sm text-gray-800 truncate pr-2">${title}</h4>
                <span class="text-xs text-gray-500 whitespace-nowrap">${timeDisplay}</span>
            </div>
            <p class="text-xs text-gray-500 font-mono truncate group-hover:text-blue-600">${file}</p>
        `;

        item.onclick = () => readMail(file);
        mailList.appendChild(item);
    });
}