import React from 'react';
import Cookies from 'js-cookie'
import './MainPage.css'

function MainPage() {
    return (
        <div>
            <h3>ACCESS_TOKEN={Cookies.get("access_token")}</h3>
            <h3>REFRESH_TOKEN={Cookies.get("refresh_token")}</h3>
            <h3>SCOPE={Cookies.get("twitch_scope")}</h3>
        </div>
    );
}

export default MainPage;
