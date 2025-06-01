/**
 * @author Burhan Arif
 * dashboard.js
 * Made for TravelPartner.git
 * @see https://github.com/burhanarif4211/TravelPartner.git
 */
/** MAPS SECTION */

function convertCords(lat, long) {
    return ol.proj.fromLonLat([long, lat]);
}

const INITIAL_VIEW = {
    longitude: 24.87899103407151,
    latitude: 67.04817999974524,
    zoom: 17
};


// Initialize map
let map;
let clickMarker;

function initMap() {
    map = new ol.Map({
        target: 'map',
        view: new ol.View({
            center: convertCords(INITIAL_VIEW.longitude, INITIAL_VIEW.latitude),
            zoom: INITIAL_VIEW.zoom
        }),
        layers: [
            new ol.layer.Tile({
                source: new ol.source.OSM()
            })
        ],

    });

    // Add click event listener
    map.on('click', function (evt) {
        const coords = ol.proj.transform(evt.coordinate, 'EPSG:3857', 'EPSG:4326');
        const [lon, lat] = coords;

        // Update display
        document.getElementById('coordinates').innerHTML =
            `Selected Location: ${lat.toFixed(4)}, ${lon.toFixed(4)}`;

        // Update hidden inputs
        if (document.getElementById('route_type').value == 'toRoutes') {
            document.getElementById('startPoint').value = `${lat},${lon}`;
        } else if (document.getElementById('route_type').value == 'fromRoutes') {
            document.getElementById('endPoint').value = `${lat},${lon}`;
        }

        // Add/update marker
        if (clickMarker) {
            map.removeLayer(clickMarker);
        }

        clickMarker = new ol.layer.Vector({
            source: new ol.source.Vector({
                features: [
                    new ol.Feature({
                        geometry: new ol.geom.Point(ol.proj.fromLonLat([lon, lat]))
                    })
                ]
            }),
            style: new ol.style.Style({
                image: new ol.style.Circle({
                    radius: 6,
                    fill: new ol.style.Fill({ color: 'red' }),
                    stroke: new ol.style.Stroke({
                        color: 'white', width: 2
                    })
                })
            })
        });

        map.addLayer(clickMarker);
    });
}

function clearCoordinates() {
    document.getElementById('coordinates').innerHTML = '';
    document.getElementById('startPoint').value = '';
    document.getElementById('endPoint').value = '';
    if (clickMarker) {
        map.removeLayer(clickMarker);
    }
}
// Initialize the map when the page loads
window.addEventListener('load', initMap);

/** MAPS */

/** Conditional rendering for connections, requests */

document.addEventListener('DOMContentLoaded', async function () {
    const token = localStorage.getItem('token');
    const userId = localStorage.getItem('userId');
    // Check if user is already connected
    loadUserInfo(token,userId);
    try {
        const response = await fetch('http://localhost:7000/api/connect/list', {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${token}`
            }
        });

        const data = await response.json();

        if (data.connections.length == 0) {
            showRequestView();
            loadReceivedRequests();
        }
        else {
            showConnectedView(data.connections);
        }
    } catch (error) {
        console.error('Error checking connection status:', error);

    }
});

async function loadUserInfo(token, userId) {
    try {
        const response = await fetch(`http://localhost:7000/api/user/${userId}`, {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${token}`
            }
        });

        if (!response.ok) {
            throw new Error('Failed to fetch user info');
        }

        const data = await response.json();
        
        // Update user info
        const userInfoContainer = document.querySelector('.user-info');
        
        // Generate initials from name
        const initials = data.name 
            ? data.name.split(' ').map(n => n[0]).join('').toUpperCase()
            : userId.charAt(0).toUpperCase();
        
        // Create user info HTML
        userInfoContainer.innerHTML = `
            <div class="avatar" id="user-avatar">${initials}</div>
            <div class="user-details">
                <div class="user-name">${capitalizeFirstLetter(data.name) || 'User'}</div>
                <div class="user-meta">
                    <span class="transport-type">${data.transportationType || 'No transport specified'}</span>
                    ${data.available ? '<span class="availability available">Available</span>' : '<span class="availability unavailable">Not available</span>'}
                </div>
            </div>
            <button class="logout-btn" onclick="logout()">Logout</button>
        `;
        
        // Store user initials for later use
        localStorage.setItem('userInitial', initials);
        
    } catch (error) {
        console.error('Error loading user info:', error);
        // Fallback to basic user info if API fails
        document.getElementById('user-avatar').textContent = userId.charAt(0).toUpperCase();
    }
}

async function showConnectedView(con) {
    let conView = document.getElementById("connected-section");
    let partnerDetails = document.getElementById("partner-details");
    let details = await fetchUserDetails(con);
    const html = `
    <div class="request-item card">
        <div class="request-avatar" style="background-color: #${details.name.slice(0, 6)};">${details.name.charAt(0).toUpperCase()}</div>
        <div class="request-info">
            <div class="request-user">${capitalizeFirstLetter(details.name) || 'Travel Partner'}</div>
            <div class="request-user">${details.phoneNumber|| 'Phone Number'}</div>
            <div class="request-details">
                <div><strong>Transport:</strong> ${details.transportationType}</div>
                ${details.toRoute ? `
                <div>
                    <strong>To Route:</strong> ${details.toRoute.startAddress} → ${details.toRoute.endPoint}
                    <br>${formatTime(details.toRoute.startTimestamp)} • ${details.toRoute.generalArea}
                </div>
                ` : ''}
                ${details.fromRoute ? `
                <div>
                    <strong>From:</strong> ${details.fromRoute.endAddress} → ${details.fromRoute.startPoint}
                    <br>${formatTime(details.fromRoute.startTimestamp)} • ${details.fromRoute.generalArea}
                </div>
                ` : 'No from route added'}
            </div>
            <button class="action-btn accept-btn" onclick="deleteConnection('${con}')">Disregard Connection</button>
        </div>

    </div>
    `;

    conView.style.display = "block";
    partnerDetails.innerHTML = html;
}
async function deleteConnection(con) {
    const token = localStorage.getItem('token');
    const response = fetch(`http://localhost:7000/api/connection/${con}` ,
    {
        method: 'DELETE',
        headers: {
            'Authorization': `Bearer ${token}`
        }
    });
    //TODO: add delay and confirmation of con deletion.
    
    showRequestView();
    await loadReceivedRequests();

}
function showRequestView() {
    let reqView = document.getElementById("requests-section");
    let conView = document.getElementById("connected-section");
    reqView.style.display = "block";
    conView.style.display = "none";
}
//fetch from /api/user/${userId}
async function fetchUserDetails(userId) {
    const token = localStorage.getItem('token');
    try {
        const response = await fetch(`http://localhost:7000/api/user/${userId}`, {
            headers: {
                'Authorization': `Bearer ${token}`
            }
        });
        if (response.ok) {
            return await response.json();
        }
        return null;
    } catch (error) {
        console.error('Error fetching user details:', error);
        return null;
    }
}
// Replace your existing loadReceivedRequests function with this
async function loadReceivedRequests() {
    const token = localStorage.getItem('token');
    const container = document.getElementById('received-requests');
    container.innerHTML = '<div class="no-requests"><p>Loading connection requests...</p></div>';

    try {
        const response = await fetch('http://localhost:7000/api/connect/requests', {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${token}`
            }
        });

        const data = await response.json();

        if (response.ok && data.status === 'success') {
            if (data.requests && data.requests.length > 0) {
                container.innerHTML = '';

                // Process each user in parallel
                await Promise.all(data.requests.map(async userId => {
                    const details = await fetchUserDetails(userId);
                    renderRequestItem(userId, details, container);
                }));

                // If any requests failed to load details
                if (container.innerHTML === '') {
                    container.innerHTML = '<div class="no-requests"><p>Error loading request details</p></div>';
                }
            } else {
                container.innerHTML = '<div class="no-requests"><p>No connection requests at this time.</p></div>';
            }
        } else {
            container.innerHTML = `<div class="error">${data.message || 'Error loading requests'}</div>`;
        }
    } catch (error) {
        container.innerHTML = `<div class="error">Network error: ${error.message}</div>`;
    }
}
 const formatTime = timestamp => {
    if (!timestamp) return '';
    const date = new Date(timestamp);
    
    // Get date components
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    
    // Get time components in 12-hour format
    let hours = date.getHours();
    const ampm = hours >= 12 ? 'PM' : 'AM';
    hours = hours % 12;
    hours = hours ? hours : 12; // Convert 0 to 12
    const minutes = String(date.getMinutes()).padStart(2, '0');
    
    return `${year}-${month}-${day} ${hours}:${minutes} ${ampm}`;
};
// recived reqs
function renderRequestItem(userId, details, container) {
    if (!details) return;

    // Generate initials from name
    const initials = details.name
        ? details.name.split(' ').map(n => n[0]).join('').toUpperCase()
        : userId.charAt(0).toUpperCase();

    const html = `
        <div class="request-item card" id="request-${userId}">
            <div class="request-avatar" style="background-color: #${userId.slice(0, 6)};">${initials}</div>
            <div class="request-info ">
                <div class="request-user">${capitalizeFirstLetter(details.name) || 'Travel Partner'}</div>
                <div class="request-details">
                    <div><strong>Transport:</strong> ${details.transportationType}</div>
                    ${details.toRoute ? `
                    <div>
                        <strong>To:</strong> ${details.toRoute.startAddress} → ${details.toRoute.endPoint}
                        <br>${formatTime(details.toRoute.startTimestamp)} • ${details.toRoute.generalArea}
                    </div>
                    ` : ''}
                    ${details.fromRoute ? `
                    <div>
                        <strong>From:</strong> ${details.fromRoute.endAddress} → ${details.fromRoute.startPoint}
                        <br>${formatTime(details.fromRoute.startTimestamp)} • ${details.fromRoute.generalArea}
                    </div>
                    ` : ''}
                </div>
            </div>
            <div class="request-actions">
                <button class="action-btn accept-btn" onclick="respondToRequest('${userId}', 'accept')">Accept</button>
                <button class="action-btn decline-btn" onclick="respondToRequest('${userId}', 'decline')">Decline</button>
            </div>
        </div>
    `;
    container.innerHTML += html;
}

// Send connection request
// async function sendConnectionRequest(targetUserId) {
//     const token = localStorage.getItem('token');

//     try {
//         const response = await fetch('http://localhost:7000/api/connect/request', {
//             method: 'POST',
//             headers: {
//                 'Content-Type': 'application/json',
//                 'Authorization': `Bearer ${token}`
//             },
//             body: JSON.stringify({ targetUserId })
//         });

//         const data = await response.json();

//         if (response.ok && data.status === 'success') {
//             alert(`Connection request sent to user ${targetUserId}`);
//         } else {
//             alert(`Failed to send request: ${data.message || 'Please try again'}`);
//         }
//     } catch (error) {
//         alert(`Network error: ${error.message}`);
//     }
// }
async function sendConnectionRequest(targetUserId) {
    const token = localStorage.getItem('token');
    const resultContainer = document.getElementById(`connection-result-${targetUserId}`);

    // Clear previous result and show loading
    resultContainer.innerHTML = 'Sending request...';
    resultContainer.className = 'connection-result';

    try {
        const response = await fetch('http://localhost:7000/api/connect/request', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify({ targetUserId })
        });

        const data = await response.json();

        if (response.ok && data.status === 'success') {
            resultContainer.innerHTML = data.message;
            resultContainer.className = 'connection-result success';
        } else {
            resultContainer.innerHTML = data.message || 'Connection request failed';
            resultContainer.className = 'connection-result error';
        }
    } catch (error) {
        resultContainer.innerHTML = 'Network error: ' + error.message;
        resultContainer.className = 'connection-result error';
    }
}
// accept or decline
async function respondToRequest(requesterId, action) {
    const token = localStorage.getItem('token');
    const requestElement = document.getElementById(`request-${requesterId}`);

    if (action === 'accept') {
        try {
            // Show loading state
            requestElement.querySelector('.request-actions').innerHTML = '<span>Processing...</span>';

            const response = await fetch('http://localhost:7000/api/connect/accept', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${token}`
                },
                body: JSON.stringify({ requesterId })
            });

            const data = await response.json();

            if (response.ok && data.status === 'success') {
                // Show success message
                requestElement.querySelector('.request-actions').innerHTML =
                    '<span style="color: green;">Accepted!</span>';

                // Update UI to show connected state
                setTimeout(() => {
                    showConnectedView(requesterId);
                    let reqView = document.getElementById("requests-section");
                    reqView.style.display = "none";

                }, 1500);
            } else {
                requestElement.querySelector('.request-actions').innerHTML =
                    `<span style="color: red;">${data.message || 'Error accepting request'}</span>`;
            }
        } catch (error) {
            requestElement.querySelector('.request-actions').innerHTML =
                '<span style="color: red;">Network error</span>';
        }
    } else if (action === 'decline') {
        // Remove from UI temporarily
        requestElement.remove();

        // If no requests left, show message
        if (document.querySelectorAll('.request-item').length === 0) {
            document.getElementById('received-requests').innerHTML =
                '<div class="no-requests"><p>No connection requests at this time.</p></div>';
        }
    }
}


/** condtional rendering connections srequests etc */


