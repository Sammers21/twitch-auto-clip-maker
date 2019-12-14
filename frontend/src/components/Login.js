import React from 'react';
import './Login.css';
import './twitch-button.css';

function Login() {
    return (
        <div className="App">
            <header className="App-header">
                <div id="sign-in-window">
                    <p>Sign-in</p>
                    <a href="https://api.twitch.tv/kraken/oauth2/authorize?response_type=code&client_id=qrwctu82red26ek1d320lpr4uqbz9e&redirect_uri=http://clip-maker.com/redirect-from-twitch&scope=user:read:email clips:edit">
                        <button id="twitch-button" type="button" className="btn btn-twitch">
                            <p id="twitch-login-text">Twitch</p>
                        </button>
                    </a>
                </div>
            </header>
        </div>
    );
}

export default Login;
