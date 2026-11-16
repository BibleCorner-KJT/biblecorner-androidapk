import { ApkSigner, SigningKey } from 'apk_sign_ts';
import fs from 'fs';
const [,, inp, out, key, cert] = process.argv;
const signer = new ApkSigner({ signingKey: SigningKey.fromPEM(fs.readFileSync(key,'utf8'), fs.readFileSync(cert,'utf8')) });
const r = await signer.sign(new Uint8Array(fs.readFileSync(inp)));
fs.writeFileSync(out, r.signedApk); console.log('signed', r.signedApk.length, 'bytes');
