import React from 'react';
import Cookies from 'js-cookie'
import './MainPage.css'
import axios from 'axios'


class MainPage extends React.Component {
    constructor(x) {
        super(x);
        this.state = {
            access_token: Cookies.get("access_token"),
            refresh_token: Cookies.get("refresh_token"),
            twitch_scope: Cookies.get("twitch_scope"),
            username: 'USER_NAME',
            email: 'EMAIL',
            profile_image_url: "/favicon.ico"
        };
        this.loadState();
    }

    loadState() {
        axios.get('https://api.twitch.tv/helix/users', {
                headers: {Authorization: "Bearer " + Cookies.get("access_token")}
            }
        )
            .then(response => {
                console.log(response);
                this.setState(
                    {
                        ...this.state,
                        username: response.data.data[0].login,
                        email: response.data.data[0].email,
                        profile_image_url: response.data.data[0].profile_image_url
                    })
            })
    }

    render() {
        return (
            <div id="main-page">
                <nav class="border-colored ">
                    <img class="nav-elem border-colored " id="profile-img" src={this.state.profile_image_url}
                         alt="Profile pic"/>
                    <p class="nav-elem border-colored ">{this.state.username}</p>
                    <p class="nav-elem border-colored ">{this.state.email}</p>
                </nav>
                <h3>ACCESS_TOKEN={this.state.access_token}</h3>
                <h3>REFRESH_TOKEN={this.state.refresh_token}</h3>
                <h3>SCOPE={this.state.twitch_scope}</h3>
            </div>
        );
    }
}

export default MainPage;
