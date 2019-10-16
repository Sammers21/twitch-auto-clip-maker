import React from 'react';
import logo from './logo.svg';
import './App.css';

function App() {
    return (
        <div className="App">
            <header className="App-header">
                <img src={logo} className="App-logo" alt="logo"/>
                <p>
                    Edit <code>src/App.js</code> and save to reload.
                </p>
                <a
                href="https://api.twitch.tv/kraken/oauth2/authorize?response_type=code&client_id=vb3b4l61t5i2af2svkksagbxuxm26x&redirect_uri=http://clip-maker.com&scope=user:read:email">Connect With Twitch!</a>
            </header>
        </div>
    );
}

export default App;
