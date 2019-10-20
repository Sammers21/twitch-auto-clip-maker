import React from 'react';
import './Login.css';
import './twitch-button.css';

function Login() {
    return (
        <div className="App">
            <header className="App-header">
                <div id="sign-in-window">
                    <p>Sign-in</p>
                    <a href="https://api.twitch.tv/kraken/oauth2/authorize?response_type=code&client_id=vb3b4l61t5i2af2svkksagbxuxm26x&redirect_uri=http://clip-maker.com/redirect-from-twitch/&scope=user:read:email">
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
