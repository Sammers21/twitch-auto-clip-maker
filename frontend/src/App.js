import React from 'react';
import {Route, Switch} from 'react-router-dom';

import Login from "./Login";
import MainPage from "./MainPage";

class App extends React.Component {
    render() {
        const App = () => (
            <div>
                <Switch>
                    <Route exact path='/login' component={Login} />
                    <Route exact path='/' component={MainPage} />
                </Switch>
            </div>
        )
        return (
            <Switch>
                <App />
            </Switch>
        );
    }
}

export default App;
