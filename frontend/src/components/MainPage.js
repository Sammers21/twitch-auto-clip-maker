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
            email: 'EMAIL'
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
                        email: response.data.data[0].email
                    })
            })
    }

    render() {
        return (
            <div>
                <h3>ACCESS_TOKEN={Cookies.get("access_token")}</h3>
                <h3>REFRESH_TOKEN={Cookies.get("refresh_token")}</h3>
                <h3>SCOPE={Cookies.get("twitch_scope")}</h3>
                <h3>USERNAME={this.state.username}</h3>
                <h3>EMAIL={this.state.email}</h3>
            </div>
        );
    }
}

export default MainPage;
