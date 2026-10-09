const CryptoJS = require("crypto-js");

function enc(val) {
    var rkEncryptionKey =
        CryptoJS.enc.Base64.parse('u/Gu5posvwDsXUnV5Zaq4g==');

    var rkEncryptionIv =
        CryptoJS.enc.Base64.parse('5D9r9ZVzEYYgha93/aUK2w==');

    var encrypted = CryptoJS.AES.encrypt(
        val.toString(),
        rkEncryptionKey,
        {
            mode: CryptoJS.mode.CBC,
            padding: CryptoJS.pad.Pkcs7,
            iv: rkEncryptionIv
        }
    );

    return encrypted.ciphertext.toString(CryptoJS.enc.Base64);
}

console.log(enc("1147"));