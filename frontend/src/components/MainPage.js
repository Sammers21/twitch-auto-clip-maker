import React from 'react';
import Cookies from 'js-cookie'
import './MainPage.css'
import axios from 'axios'


class MainPage extends React.Component {
    constructor(x) {
        super(x);
        this.state = {
            access_token: Cookies.get("access_token") === undefined ? "gmgdbwffus6wzwvvf02agytbx59dzv" : Cookies.get("access_token"),
            refresh_token: Cookies.get("refresh_token"),
            twitch_scope: Cookies.get("twitch_scope"),
            username: 'USER_NAME',
            email: 'EMAIL',
            profile_image_url: "/favicon.ico",
            input_chan: '',
            clips: []
        };
        this.loadState();
    }

    loadState() {
        axios.get('https://api.twitch.tv/helix/users', {
            headers: {Authorization: "Bearer " + this.state.access_token}
            }
        )
            .then(response => {
                console.log("User response", response);
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
                <nav className="border-colored ">
                    <img className="nav-elem border-colored " id="profile-img" src={this.state.profile_image_url}
                         alt="Profile pic"/>
                    <p className="nav-elem border-colored ">{this.state.username}</p>
                    <p className="nav-elem border-colored ">{this.state.email}</p>
                </nav>
                <form className="border-colored">
                    <label>
                        Channel:
                        <input type="text"  value={this.state.input_chan}  onChange={evt => this.updateInputValue(evt)}/>
                    </label>
                    <input type="button" value="Submit" onClick={event => this.loadClips(event)}/>
                </form>
                <div id="clip-list">
                    {this.state.clips.map(value => {
                        return (
                            <div key={value.slug} className="border-colored">
                                <iframe src={value.embed_url + '&autoplay=false'}  title={value.slug} width='640' height='360' frameBorder='0' scrolling='no' allowFullScreen={true}></iframe>
                            </div>
                        )
                    })}
                </div>
            </div>
        );
    }

    loadClips() {
        axios.get('https://api.twitch.tv/kraken/clips/top', {
                headers: {
                    Authorization: "OAuth " + this.state.access_token,
                    Accept: 'application/vnd.twitchtv.v5+json'
                }
            }
        )
            .then(response => {
                console.log("Clips response", response);
                this.setState({
                    ...this.state,
                    clips: response.data.clips
                });
            })
    }

    updateInputValue(event) {
        this.setState({
            ...this.state,
            input_chan: event.target.value
        });
    }
}

export default MainPage;
