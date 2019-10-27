import React from 'react';
import Cookies from 'js-cookie'
import './MainPage.css'
import axios from 'axios'


class MainPage extends React.Component {
    constructor(x) {
        super(x);
        this.state = {
            access_token: Cookies.get("access_token") === undefined ? "x5lrm2mc3n5emipza9mblys4rx7j5b" : Cookies.get("access_token"),
            refresh_token: Cookies.get("refresh_token"),
            twitch_scope: Cookies.get("twitch_scope"),
            username: 'USER_NAME',
            email: 'EMAIL',
            profile_image_url: "/favicon.ico",
            input_chan: ''
        };
        this.loadState();
    }

    loadState() {
        axios.get('https://api.twitch.tv/helix/users', {
            headers: {Authorization: "Bearer " + this.state.access_token}
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
                <form class="border-colored">
                    <label>
                        Channel:
                        <input type="text"  value={this.state.input_chan}  onChange={evt => this.updateInputValue(evt)}/>
                    </label>
                    <input type="submit" value="Submit" />
                </form>
            </div>
        );
    }

    updateInputValue(event) {
        this.setState({
            ...this.state,
            input_chan: event.target.value
        });
    }
}

export default MainPage;
